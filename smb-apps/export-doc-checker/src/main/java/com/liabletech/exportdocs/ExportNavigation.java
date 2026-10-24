package com.liabletech.exportdocs;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.liabletech.exportdocs.shipment.Shipments;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.PlanCatalog;
import com.liabletech.platform.web.AppNavigation;
import com.liabletech.platform.web.NavItem;

@Component
public class ExportNavigation implements AppNavigation, PlanCatalog {

	static final String MONTHLY = "MONTHLY";

	static final String PER_SHIPMENT = "PER_SHIPMENT";

	static final BigDecimal MONTHLY_PRICE = BigDecimal.valueOf(999);

	static final int MONTHLY_INCLUDED = 10;

	static final BigDecimal EXTRA_SHIPMENT = BigDecimal.valueOf(99);

	static final BigDecimal SHIPMENT_PRICE = BigDecimal.valueOf(199);

	private final Shipments shipments;

	private final Clock clock;

	public ExportNavigation(Shipments shipments, Clock clock) {
		this.shipments = shipments;
		this.clock = clock;
	}

	@Override
	public List<NavItem> items() {
		return List.of(new NavItem("Dashboard", "/"), new NavItem("Shipments", "/shipments"), new NavItem("Team", "/team"));
	}

	@Override
	public String defaultPlanCode() {
		return MONTHLY;
	}

	@Override
	public List<PlanOption> options() {
		return List.of(new PlanOption(MONTHLY, "Monthly", "₹999/month, 10 shipments included"),
				new PlanOption(PER_SHIPMENT, "Per shipment", "₹199 per shipment checked"));
	}

	/** A shipment counts in the month its documents were first checked; re-checks are free. */
	@Override
	public PlanSummary summarize(Account account) {
		LocalDate monthStart = LocalDate.now(clock).withDayOfMonth(1);
		long checked = shipments.countFirstCheckedSince(account.id(),
				monthStart.atStartOfDay(clock.getZone()).toOffsetDateTime());
		String count = checked + (checked == 1 ? " shipment" : " shipments") + " checked this month";
		if (PER_SHIPMENT.equals(account.planCode())) {
			return new PlanSummary("Per shipment", "₹199 per shipment checked",
					SHIPMENT_PRICE.multiply(BigDecimal.valueOf(checked)),
					List.of(count, "Re-checks after corrections are free", "No monthly fee"));
		}
		long extra = Math.max(0, checked - MONTHLY_INCLUDED);
		return new PlanSummary("Monthly", "₹999 per month, " + MONTHLY_INCLUDED + " shipments included, then ₹99 each",
				MONTHLY_PRICE.add(EXTRA_SHIPMENT.multiply(BigDecimal.valueOf(extra))),
				List.of(count, extra > 0 ? extra + " beyond the " + MONTHLY_INCLUDED + " included" : "Re-checks after corrections are free",
						"Unlimited users and WhatsApp senders"));
	}

}
