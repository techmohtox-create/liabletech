package com.liabletech.parts.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.liabletech.parts.PartsNavigation;
import com.liabletech.parts.booking.Booking;
import com.liabletech.parts.booking.Bookings;
import com.liabletech.parts.catalog.Parts;
import com.liabletech.parts.enquiry.Enquiries;
import com.liabletech.parts.enquiry.Enquiry;
import com.liabletech.parts.report.Reports;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.account.PlanCatalog;
import com.liabletech.platform.security.CurrentUser;

@Controller
public class DashboardController {

	private final Reports reports;

	private final Enquiries enquiries;

	private final Bookings bookings;

	private final Parts parts;

	private final Accounts accounts;

	private final PlanCatalog plans;

	private final Clock clock;

	public DashboardController(Reports reports, Enquiries enquiries, Bookings bookings, Parts parts, Accounts accounts,
			PlanCatalog plans, Clock clock) {
		this.reports = reports;
		this.enquiries = enquiries;
		this.bookings = bookings;
		this.parts = parts;
		this.accounts = accounts;
		this.plans = plans;
		this.clock = clock;
	}

	@GetMapping("/")
	public String dashboard(@AuthenticationPrincipal CurrentUser user, Model model) {
		long account = user.accountId();
		OffsetDateTime now = OffsetDateTime.now(clock);
		OffsetDateTime today = LocalDate.now(clock).atStartOfDay(clock.getZone()).toOffsetDateTime();
		var shop = accounts.get(account);
		model.addAttribute("today", reports.stats(account, today, now.plusMinutes(1)));
		model.addAttribute("week", reports.stats(account, today.minusDays(6), now.plusMinutes(1)));
		model.addAttribute("waiting", enquiries.list(account, List.of(Enquiry.NEW), null, 6));
		model.addAttribute("toHandOver", bookings.list(account, List.of(Booking.BOOKED), null, 6));
		model.addAttribute("low", parts.runningLow(account, now.minusDays(30), 6));
		model.addAttribute("missed", reports.missed(account, today.minusDays(6), 5));
		model.addAttribute("pro", PartsNavigation.isPro(shop));
		model.addAttribute("plan", plans.summarize(shop));
		model.addAttribute("partCount", parts.countActive(account));
		return "parts/dashboard";
	}

}
