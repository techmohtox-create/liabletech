package com.liabletech.amc;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.PlanCatalog;
import com.liabletech.platform.web.AppNavigation;
import com.liabletech.platform.web.NavItem;

@Component
public class AmcNavigation implements AppNavigation, PlanCatalog {

	static final BigDecimal PRICE = BigDecimal.valueOf(199);

	@Override
	public List<NavItem> items() {
		return List.of(new NavItem("Dashboard", "/"), new NavItem("Renewals", "/contracts"),
				new NavItem("Requests", "/requests"), new NavItem("Customers", "/customers"), new NavItem("Labels", "/labels"),
				new NavItem("Setup", "/setup"));
	}

	@Override
	public String defaultPlanCode() {
		return "BASIC";
	}

	@Override
	public PlanSummary summarize(Account account) {
		return new PlanSummary("AMC Renewals", "₹199 per month", PRICE,
				List.of("Unlimited customers, contracts and technicians", "Reminders and booking links included",
						"WhatsApp messages at Meta's conversation rates when you connect your own number"));
	}

}
