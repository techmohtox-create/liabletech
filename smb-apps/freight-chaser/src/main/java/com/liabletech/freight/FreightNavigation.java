package com.liabletech.freight;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.PlanCatalog;
import com.liabletech.platform.web.AppNavigation;
import com.liabletech.platform.web.NavItem;

@Component
public class FreightNavigation implements AppNavigation, PlanCatalog {

	@Override
	public List<NavItem> items() {
		return List.of(new NavItem("Dashboard", "/"), new NavItem("PODs", "/pods"), new NavItem("Trips", "/trips"),
				new NavItem("Invoices", "/invoices"), new NavItem("Parties", "/parties"), new NavItem("Fleet", "/fleet"),
				new NavItem("Chasing", "/chasing"));
	}

	@Override
	public String defaultPlanCode() {
		return "FLEET";
	}

	/** ₹299 a month per fleet, however many trucks and trips. */
	@Override
	public PlanSummary summarize(Account account) {
		return new PlanSummary("Fleet", "₹299 per month per fleet", BigDecimal.valueOf(299),
				List.of("Unlimited trucks, drivers and trips", "POD photos read automatically",
						"Invoices with proof attached, sent by WhatsApp and email",
						"Automatic payment follow-ups until paid"));
	}

}
