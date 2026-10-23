package com.liabletech.materials.dashboard;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.liabletech.materials.bill.Bills;
import com.liabletech.materials.delivery.Deliveries;
import com.liabletech.materials.leakage.Flags;
import com.liabletech.platform.security.CurrentUser;

/** Rupees at risk and recovered, where the gaps are, and what needs attention today. */
@Controller
public class DashboardController {

	private final Flags flags;

	private final Deliveries deliveries;

	private final Bills bills;

	private final Clock clock;

	public DashboardController(Flags flags, Deliveries deliveries, Bills bills, Clock clock) {
		this.flags = flags;
		this.deliveries = deliveries;
		this.bills = bills;
		this.clock = clock;
	}

	@GetMapping("/")
	public String dashboard(@AuthenticationPrincipal CurrentUser user, Model model) {
		long accountId = user.accountId();
		LocalDate monthStart = LocalDate.now(clock).withDayOfMonth(1);
		model.addAttribute("totals", flags.totals(accountId, monthStart.atStartOfDay(clock.getZone()).toOffsetDateTime()));
		model.addAttribute("awaiting", deliveries.countAwaiting(accountId));
		model.addAttribute("billsToCheck", bills.countNeedingReview(accountId));
		model.addAttribute("bySite", flags.bySite(accountId));
		model.addAttribute("bySupplier", flags.bySupplier(accountId));
		model.addAttribute("recentFlags", flags.list(accountId, "AT_RISK", null, null, 8));
		model.addAttribute("recentDeliveries", deliveries.list(accountId, null, null, 6));
		return "materials/dashboard";
	}

}
