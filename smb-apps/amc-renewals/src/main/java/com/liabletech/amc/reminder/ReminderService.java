package com.liabletech.amc.reminder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.liabletech.amc.contract.Contract;
import com.liabletech.amc.contract.Contracts;
import com.liabletech.amc.customer.Appliance;
import com.liabletech.amc.customer.Appliances;
import com.liabletech.amc.customer.Customer;
import com.liabletech.amc.customer.Customers;
import com.liabletech.amc.plan.Plans;
import com.liabletech.amc.request.ServiceRequest;
import com.liabletech.amc.request.ServiceRequests;
import com.liabletech.platform.AppProperties;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.messaging.Messenger;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.messaging.Outgoing;
import com.liabletech.platform.text.IndianMoney;

/**
 * The renewal engine. Every morning each contract gets the reminder that has fallen due, once: a
 * month before it ends, closer to the date, on the day and after it lapses. Contracts in force also
 * get a nudge when an included service visit is due.
 */
@Service
public class ReminderService {

	private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

	/** A reminder missed by more than this (the app was off) is skipped rather than sent late. */
	static final int GRACE_DAYS = 3;

	/** Service reminders go this many days before the visit is due. */
	static final int SERVICE_LEAD_DAYS = 3;

	private final Contracts contracts;

	private final Customers customers;

	private final Appliances appliances;

	private final Plans plans;

	private final Reminders reminders;

	private final ReminderSettingsRepository settings;

	private final ServiceRequests requests;

	private final Messenger messenger;

	private final Accounts accounts;

	private final AppProperties properties;

	private final JdbcClient jdbc;

	private final Clock clock;

	public ReminderService(Contracts contracts, Customers customers, Appliances appliances, Plans plans,
			Reminders reminders, ReminderSettingsRepository settings, ServiceRequests requests, Messenger messenger,
			Accounts accounts, AppProperties properties, JdbcClient jdbc, Clock clock) {
		this.contracts = contracts;
		this.customers = customers;
		this.appliances = appliances;
		this.plans = plans;
		this.reminders = reminders;
		this.settings = settings;
		this.requests = requests;
		this.messenger = messenger;
		this.accounts = accounts;
		this.properties = properties;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Scheduled(cron = "${app.reminders.cron}", zone = "Asia/Kolkata")
	public void daily() {
		List<Long> accountIds = jdbc.sql("SELECT DISTINCT account_id FROM contract WHERE status = 'ACTIVE'")
			.query(Long.class)
			.list();
		for (Long accountId : accountIds) {
			try {
				if (settings.get(accountId).autoSend()) {
					runFor(accountId);
				}
			}
			catch (RuntimeException ex) {
				log.warn("Reminders for account {} failed", accountId, ex);
			}
		}
	}

	/** Sends every reminder due today for one business; returns how many went out. */
	public int runFor(long accountId) {
		ReminderSettings config = settings.get(accountId);
		LocalDate today = LocalDate.now(clock);
		int sent = 0;
		List<Integer> offsets = config.offsets();
		if (!offsets.isEmpty()) {
			int earliest = offsets.getFirst();
			int latest = offsets.getLast();
			for (Contract contract : contracts.endingBetween(accountId, today.minusDays(latest + GRACE_DAYS),
					today.minusDays(earliest))) {
				Optional<Integer> due = dueOffset(contract, offsets, reminders.sent(contract.id(), Reminders.RENEWAL), today);
				if (due.isPresent() && requests.openFor(contract.id(), ServiceRequest.RENEWAL).isEmpty()) {
					sendRenewal(accountId, contract, due.get(), today);
					sent++;
				}
			}
		}
		if (config.serviceReminders()) {
			for (Contract contract : contracts.inForce(accountId, today)) {
				Optional<Visit> visit = dueVisit(contract, requests.visitsDone(contract.id()),
						reminders.sent(contract.id(), Reminders.SERVICE), today);
				if (visit.isPresent() && requests.openFor(contract.id(), ServiceRequest.SERVICE).isEmpty()) {
					sendService(accountId, contract, visit.get());
					sent++;
				}
			}
		}
		return sent;
	}

	/**
	 * The renewal reminder to send today: the latest offset that has fallen due within the grace days
	 * and has not been sent. Older missed ones are not sent on top of it.
	 */
	public static Optional<Integer> dueOffset(Contract contract, List<Integer> offsets, Set<Integer> alreadySent, LocalDate today) {
		Integer best = null;
		for (Integer offset : offsets) {
			LocalDate dueOn = contract.endsOn().plusDays(offset);
			if (dueOn.isAfter(today) || dueOn.isBefore(today.minusDays(GRACE_DAYS))) {
				continue;
			}
			if (alreadySent.stream().anyMatch((s) -> s >= offset)) {
				continue;
			}
			best = offset;
		}
		return Optional.ofNullable(best);
	}

	/**
	 * The included service visit to remind about: visit n is due at the middle of the n-th part of the
	 * contract year (3 visits a year: months 2, 6 and 10).
	 */
	public static Optional<Visit> dueVisit(Contract contract, long visitsDone, Set<Integer> alreadySent, LocalDate today) {
		List<Visit> visits = visitDates(contract);
		for (Visit visit : visits) {
			if (visit.number() <= visitsDone || alreadySent.contains(visit.number())) {
				continue;
			}
			if (!visit.dueOn().minusDays(SERVICE_LEAD_DAYS).isAfter(today) && !visit.dueOn().plusDays(14).isBefore(today)) {
				return Optional.of(visit);
			}
		}
		return Optional.empty();
	}

	public static List<Visit> visitDates(Contract contract) {
		List<Visit> visits = new ArrayList<>();
		int count = contract.visitsIncluded();
		if (count <= 0) {
			return visits;
		}
		long length = ChronoUnit.DAYS.between(contract.startsOn(), contract.endsOn()) + 1;
		for (int n = 1; n <= count; n++) {
			long day = Math.round(length * (n - 0.5) / count);
			visits.add(new Visit(n, contract.startsOn().plusDays(day)));
		}
		return visits;
	}

	/** Sends the renewal reminder for a contract now, whatever the schedule says (the office's "Remind now"). */
	public OutboundMessage remindNow(long accountId, long contractId) {
		Contract contract = contracts.get(accountId, contractId);
		if (!contract.open()) {
			throw new IllegalStateException("This contract is already " + contract.status().toLowerCase() + ".");
		}
		if (customers.get(accountId, contract.customerId()).optedOut()) {
			throw new IllegalStateException("This customer asked not to get reminders.");
		}
		LocalDate today = LocalDate.now(clock);
		int offset = (int) ChronoUnit.DAYS.between(contract.endsOn(), today);
		return sendRenewal(accountId, contract, offset, today);
	}

	private OutboundMessage sendRenewal(long accountId, Contract contract, int offset, LocalDate today) {
		Account account = accounts.get(accountId);
		Customer customer = customers.get(accountId, contract.customerId());
		Appliance appliance = appliances.get(accountId, contract.applianceId());
		BigDecimal price = renewalPrice(accountId, contract);
		String link = link(contract);
		String text = ReminderMessages.renewal(account, customer, appliance, contract, price, today, link);
		OutboundMessage message = messenger.send(Outgoing.whatsApp(accountId, customer.phone(), text)
			.about("contract", contract.id())
			.template("amc_renewal_reminder", customer.greetingName().isEmpty() ? "Customer" : customer.greetingName(),
					appliance.describe(), ReminderMessages.date(contract.endsOn()), IndianMoney.rupees(price), link));
		reminders.record(accountId, contract.id(), Reminders.RENEWAL, offset, contract.endsOn().plusDays(offset),
				OffsetDateTime.now(clock), message.id());
		return message;
	}

	private void sendService(long accountId, Contract contract, Visit visit) {
		Account account = accounts.get(accountId);
		Customer customer = customers.get(accountId, contract.customerId());
		Appliance appliance = appliances.get(accountId, contract.applianceId());
		String link = link(contract);
		OutboundMessage message = messenger.send(Outgoing.whatsApp(accountId, customer.phone(),
				ReminderMessages.serviceDue(account, customer, appliance, contract, visit.number(), link))
			.about("contract", contract.id())
			.template("amc_service_due", customer.greetingName().isEmpty() ? "Customer" : customer.greetingName(),
					appliance.describe(), String.valueOf(visit.number()), link));
		reminders.record(accountId, contract.id(), Reminders.SERVICE, visit.number(), visit.dueOn(),
				OffsetDateTime.now(clock), message.id());
	}

	/** Today's price of the plan, so a reminder never quotes last year's rate; the old price if the plan is gone. */
	public BigDecimal renewalPrice(long accountId, Contract contract) {
		return plans.find(accountId, contract.planId()).filter((p) -> p.active()).map((p) -> p.price()).orElse(contract.price());
	}

	public String link(Contract contract) {
		return properties.baseUrl() + "/p/renew/" + contract.renewalToken();
	}

	public record Visit(int number, LocalDate dueOn) {
	}

}
