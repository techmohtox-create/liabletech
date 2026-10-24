package com.liabletech.amc.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.liabletech.amc.contract.Contracts;
import com.liabletech.amc.label.LabelScan;
import com.liabletech.amc.label.LabelScans;
import com.liabletech.amc.reminder.Reminders;
import com.liabletech.amc.request.ServiceRequest;
import com.liabletech.amc.request.ServiceRequests;
import com.liabletech.platform.security.CurrentUser;

@Controller
public class DashboardController {

	private final Contracts contracts;

	private final ServiceRequests requests;

	private final LabelScans scans;

	private final Reminders reminders;

	private final Clock clock;

	public DashboardController(Contracts contracts, ServiceRequests requests, LabelScans scans, Reminders reminders,
			Clock clock) {
		this.contracts = contracts;
		this.requests = requests;
		this.scans = scans;
		this.reminders = reminders;
		this.clock = clock;
	}

	@GetMapping("/")
	public String dashboard(@AuthenticationPrincipal CurrentUser user, Model model) {
		LocalDate today = LocalDate.now(clock);
		long account = user.accountId();
		model.addAttribute("today", today);
		model.addAttribute("metrics", contracts.metrics(account, today, clock.getZone()));
		model.addAttribute("due", contracts.list(account, "DUE", today, null, 12));
		model.addAttribute("newRequests", requests.list(account, List.of(ServiceRequest.NEW), 8));
		model.addAttribute("visits", requests.list(account, List.of(ServiceRequest.SCHEDULED), 50)
			.stream()
			.filter((r) -> r.scheduledOn() != null && !r.scheduledOn().isAfter(today.plusDays(7)))
			.toList());
		model.addAttribute("labels", scans.list(account, LabelScan.NEEDS_REVIEW, 6));
		model.addAttribute("remindersThisMonth", reminders.countSince(account,
				today.withDayOfMonth(1).atStartOfDay(clock.getZone()).toOffsetDateTime()));
		return "amc/dashboard";
	}

}
