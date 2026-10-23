package com.liabletech.materials;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.liabletech.materials.site.Sites;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.PlanCatalog;
import com.liabletech.platform.web.AppNavigation;
import com.liabletech.platform.web.NavItem;

@Component
public class MaterialsNavigation implements AppNavigation, PlanCatalog {

	static final BigDecimal PRICE_PER_SITE = BigDecimal.valueOf(999);

	private final Sites sites;

	public MaterialsNavigation(Sites sites) {
		this.sites = sites;
	}

	@Override
	public List<NavItem> items() {
		return List.of(new NavItem("Dashboard", "/"), new NavItem("Deliveries", "/deliveries"),
				new NavItem("Bills", "/bills"), new NavItem("Leakage", "/leakage"), new NavItem("Sites", "/sites"),
				new NavItem("Suppliers", "/suppliers"));
	}

	@Override
	public String defaultPlanCode() {
		return "SITE";
	}

	/** ₹999 a month for each active site. */
	@Override
	public PlanSummary summarize(Account account) {
		long active = sites.countActive(account.id());
		return new PlanSummary("Per site", "₹999 per month per active site",
				PRICE_PER_SITE.multiply(BigDecimal.valueOf(Math.max(1, active))),
				List.of(active + (active == 1 ? " active site" : " active sites"), "Unlimited supervisors, slips and bills",
						"Close a site when work ends and it stops counting"));
	}

}
