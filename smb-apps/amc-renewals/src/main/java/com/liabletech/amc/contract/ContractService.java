package com.liabletech.amc.contract;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liabletech.amc.plan.AmcPlan;
import com.liabletech.amc.plan.Plans;
import com.liabletech.amc.request.ServiceRequest;
import com.liabletech.amc.request.ServiceRequests;

/** Selling, renewing and closing maintenance contracts. */
@Service
public class ContractService {

	private final Contracts contracts;

	private final Plans plans;

	private final ServiceRequests requests;

	private final Clock clock;

	public ContractService(Contracts contracts, Plans plans, ServiceRequests requests, Clock clock) {
		this.contracts = contracts;
		this.plans = plans;
		this.requests = requests;
		this.clock = clock;
	}

	/** A new contract on a plan, for its number of months from the start date. */
	public long sell(long accountId, long customerId, long applianceId, AmcPlan plan, BigDecimal price, LocalDate startsOn,
			String source, BigDecimal paid, String mode) {
		LocalDate start = startsOn != null ? startsOn : LocalDate.now(clock);
		long id = contracts.create(accountId, customerId, applianceId, plan.id(), plan.name(),
				price != null && price.signum() > 0 ? price : plan.price(), start, start.plusMonths(plan.months()).minusDays(1),
				plan.visits(), source, OffsetDateTime.now(clock));
		if (paid != null && paid.signum() > 0) {
			contracts.recordPayment(accountId, id, paid, LocalDate.now(clock), mode);
		}
		return id;
	}

	/**
	 * Renews a contract. Renewed before it ends, the new year starts the day after; renewed after it
	 * lapsed, it starts today. Open renewal requests for it are closed. Paid means paid in full.
	 */
	@Transactional
	public long renew(long accountId, long contractId, Long planId, BigDecimal price, boolean paid, String mode) {
		Contract old = contracts.get(accountId, contractId);
		if (!old.open()) {
			throw new IllegalStateException("This contract is already " + old.status().toLowerCase() + ".");
		}
		LocalDate today = LocalDate.now(clock);
		AmcPlan plan = plans.find(accountId, planId != null ? planId : old.planId())
			.orElseGet(() -> new AmcPlan(null, accountId, old.planName(), "ANY", old.price(), 12, old.visitsIncluded(), null, true));
		LocalDate start = old.endsOn().isBefore(today) ? today : old.endsOn().plusDays(1);
		BigDecimal amount = price != null && price.signum() > 0 ? price : plan.price();
		long id = sell(accountId, old.customerId(), old.applianceId(), plan, amount, start, "RENEWAL", paid ? amount : null,
				paid ? mode : null);
		contracts.markRenewed(old.id(), id);
		requests.openFor(old.id(), ServiceRequest.RENEWAL)
			.ifPresent((request) -> requests.finish(accountId, request.id(), ServiceRequest.DONE, today,
					OffsetDateTime.now(clock)));
		return id;
	}

	public void recordPayment(long accountId, long contractId, BigDecimal amount, String mode) {
		contracts.get(accountId, contractId);
		contracts.recordPayment(accountId, contractId, amount, LocalDate.now(clock), mode);
	}

	public void cancel(long accountId, long contractId) {
		contracts.get(accountId, contractId);
		contracts.cancel(accountId, contractId);
	}

}
