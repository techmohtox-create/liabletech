package com.liabletech.amc.request;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.liabletech.amc.contract.Contract;
import com.liabletech.amc.contract.ContractService;
import com.liabletech.amc.contract.Contracts;
import com.liabletech.amc.customer.Appliance;
import com.liabletech.amc.customer.Appliances;
import com.liabletech.amc.customer.Customer;
import com.liabletech.amc.customer.Customers;
import com.liabletech.amc.plan.AmcPlan;
import com.liabletech.amc.plan.Plans;
import com.liabletech.amc.reminder.ReminderMessages;
import com.liabletech.amc.team.Technician;
import com.liabletech.amc.team.Technicians;
import com.liabletech.platform.AppProperties;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.messaging.Messenger;
import com.liabletech.platform.messaging.Outgoing;
import com.liabletech.platform.text.IndianMoney;
import com.liabletech.platform.text.Phones;

/**
 * Requests from customers (the renewal link, a WhatsApp reply) and the office's follow-up: schedule
 * the visit, renew the contract, let the customer know.
 */
@Service
public class RequestService {

	private final ServiceRequests requests;

	private final Contracts contracts;

	private final ContractService contractService;

	private final Customers customers;

	private final Appliances appliances;

	private final Plans plans;

	private final Technicians technicians;

	private final Messenger messenger;

	private final Accounts accounts;

	private final AppProperties properties;

	private final Clock clock;

	public RequestService(ServiceRequests requests, Contracts contracts, ContractService contractService,
			Customers customers, Appliances appliances, Plans plans, Technicians technicians, Messenger messenger,
			Accounts accounts, AppProperties properties, Clock clock) {
		this.requests = requests;
		this.contracts = contracts;
		this.contractService = contractService;
		this.customers = customers;
		this.appliances = appliances;
		this.plans = plans;
		this.technicians = technicians;
		this.messenger = messenger;
		this.accounts = accounts;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * A request about a contract, from its link or a WhatsApp reply. Asking twice gives the same open
	 * request; the owner is told on WhatsApp the first time.
	 */
	public ServiceRequest forContract(Contract contract, String kind, String source, Long planId, LocalDate preferredOn,
			String slot, String note) {
		Optional<ServiceRequest> open = requests.openFor(contract.id(), kind);
		if (open.isPresent()) {
			return open.get();
		}
		long id = requests.create(contract.accountId(), contract.customerId(), contract.id(), contract.applianceId(), kind,
				source, planId != null ? planId : contract.planId(), preferredOn, slot, note, OffsetDateTime.now(clock));
		ServiceRequest request = requests.get(contract.accountId(), id);
		notifyOwner(request, contract);
		return request;
	}

	/** Someone who is not a customer yet wrote on WhatsApp: an enquiry to call back, and the owner is told. */
	public long enquiry(long accountId, long customerId, String note) {
		long id = requests.create(accountId, customerId, null, null, ServiceRequest.REPAIR, "WHATSAPP", null, null, null,
				note, OffsetDateTime.now(clock));
		notifyOwner(requests.get(accountId, id), null);
		return id;
	}

	/** A request the office takes on the phone. */
	public long fromOffice(long accountId, long customerId, Long contractId, Long applianceId, String kind,
			LocalDate preferredOn, String note) {
		customers.get(accountId, customerId);
		return requests.create(accountId, customerId, contractId, applianceId, kind, "OFFICE", null, preferredOn, null, note,
				OffsetDateTime.now(clock));
	}

	/** Books the visit and tells the customer the day and the technician. */
	public void schedule(long accountId, long requestId, LocalDate on, Long technicianId) {
		ServiceRequest request = requests.get(accountId, requestId);
		Optional<Technician> technician = technicians.find(accountId, technicianId);
		requests.schedule(accountId, requestId, on, technician.map(Technician::id).orElse(null), OffsetDateTime.now(clock));
		Customer customer = customers.get(accountId, request.customerId());
		if (!customer.optedOut()) {
			messenger.send(Outgoing.whatsApp(accountId, customer.phone(), ReminderMessages.visitBooked(accounts.get(accountId),
					customer, on, request.preferredSlot(), technician.map(Technician::name).orElse(null)))
				.about("request", requestId));
		}
	}

	public void done(long accountId, long requestId) {
		requests.get(accountId, requestId);
		requests.finish(accountId, requestId, ServiceRequest.DONE, LocalDate.now(clock), OffsetDateTime.now(clock));
	}

	public void dismiss(long accountId, long requestId) {
		requests.get(accountId, requestId);
		requests.finish(accountId, requestId, ServiceRequest.DISMISSED, null, OffsetDateTime.now(clock));
	}

	/** Renews the contract a renewal request is about and thanks the customer. */
	public long renew(long accountId, long requestId, Long planId, BigDecimal price, boolean paid, String mode) {
		ServiceRequest request = requests.get(accountId, requestId);
		if (request.contractId() == null) {
			throw new IllegalStateException("This request is not about a contract.");
		}
		long renewedId = contractService.renew(accountId, request.contractId(), planId != null ? planId : request.planId(),
				price, paid, mode);
		requests.finish(accountId, requestId, ServiceRequest.DONE, LocalDate.now(clock), OffsetDateTime.now(clock));
		thank(accountId, renewedId);
		return renewedId;
	}

	/** Tells the customer their contract is renewed and until when. */
	public void thank(long accountId, long contractId) {
		Contract renewed = contracts.get(accountId, contractId);
		Customer customer = customers.get(accountId, renewed.customerId());
		if (customer.optedOut()) {
			return;
		}
		Appliance appliance = appliances.get(accountId, renewed.applianceId());
		messenger.send(Outgoing.whatsApp(accountId, customer.phone(),
				ReminderMessages.renewalConfirmed(accounts.get(accountId), customer, appliance, renewed))
			.about("contract", contractId));
	}

	/** Tells the owner on WhatsApp; {@code contract} is null for an enquiry from someone new. */
	private void notifyOwner(ServiceRequest request, Contract contract) {
		Account account = accounts.get(request.accountId());
		String phone = Phones.normalize(account.phone());
		if (!StringUtils.hasText(phone)) {
			return;
		}
		Customer customer = customers.get(request.accountId(), request.customerId());
		String who = customer.displayName() + " (" + Phones.pretty(customer.phone()) + ")";
		StringBuilder text = new StringBuilder("🔔 ");
		String asked;
		if (contract == null) {
			asked = "a call back";
			text.append("New enquiry from ").append(who)
				.append(StringUtils.hasText(request.note()) ? ": " + request.note() : ".");
		}
		else if (ServiceRequest.RENEWAL.equals(request.kind())) {
			asked = "an AMC renewal";
			Appliance appliance = appliances.get(request.accountId(), contract.applianceId());
			Optional<AmcPlan> plan = plans.find(request.accountId(), request.planId());
			text.append(who).append(" wants to renew ")
				.append(plan.map(AmcPlan::name).orElse(contract.planName()))
				.append(plan.map((p) -> " at " + IndianMoney.rupees(p.price())).orElse(""))
				.append(" for the ").append(appliance.describe()).append('.');
		}
		else {
			asked = "a service visit";
			Appliance appliance = appliances.get(request.accountId(), contract.applianceId());
			text.append(who).append(" asked for a service visit for the ").append(appliance.describe());
			if (request.preferredOn() != null) {
				text.append(" on ").append(request.preferredOn());
			}
			if (request.preferredSlot() != null) {
				text.append(", ").append(request.preferredSlot().toLowerCase());
			}
			text.append('.');
		}
		text.append("\n").append(properties.baseUrl()).append("/requests");
		messenger.send(Outgoing.whatsApp(request.accountId(), phone, text.toString())
			.about("request", request.id())
			.template("amc_new_request", customer.displayName(), asked));
	}

}
