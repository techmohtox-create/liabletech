package com.liabletech.parts;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.PlanCatalog;
import com.liabletech.platform.web.AppNavigation;
import com.liabletech.platform.web.NavItem;

@Component
public class PartsNavigation implements AppNavigation, PlanCatalog {

	public static final String COUNTER = "COUNTER";

	public static final String PRO = "PRO";

	static final BigDecimal COUNTER_PRICE = BigDecimal.valueOf(299);

	static final BigDecimal PRO_PRICE = BigDecimal.valueOf(499);

	/** Pro answers sure matches by itself and nudges the owner about waiting enquiries. */
	public static boolean isPro(Account account) {
		return PRO.equals(account.planCode());
	}

	@Override
	public List<NavItem> items() {
		return List.of(new NavItem("Dashboard", "/"), new NavItem("Counter", "/inbox"), new NavItem("Bookings", "/bookings"),
				new NavItem("Stock", "/parts"), new NavItem("Mechanics", "/mechanics"), new NavItem("Reports", "/reports"),
				new NavItem("Setup", "/setup"));
	}

	@Override
	public String defaultPlanCode() {
		return COUNTER;
	}

	@Override
	public List<PlanOption> options() {
		return List.of(new PlanOption(COUNTER, "Counter", "₹299/month: photo, voice and text enquiries, one-tap quotes"),
				new PlanOption(PRO, "Pro", "₹499/month: also instant automatic quotes and waiting alerts"));
	}

	@Override
	public PlanSummary summarize(Account account) {
		if (isPro(account)) {
			return new PlanSummary("Pro", "₹499 per month", PRO_PRICE,
					List.of("Sure matches quoted automatically in seconds", "WhatsApp alert when an enquiry waits",
							"Everything in Counter"));
		}
		return new PlanSummary("Counter", "₹299 per month", COUNTER_PRICE,
				List.of("Photo, voice and text enquiries matched to your stock", "One-tap quotes and bookings",
						"Response-time and missed-demand reports"));
	}

}
