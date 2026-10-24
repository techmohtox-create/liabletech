package com.liabletech.parts.report;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.liabletech.platform.security.CurrentUser;

@Controller
public class ReportController {

	static final List<Integer> PERIODS = List.of(7, 30, 90);

	private final Reports reports;

	private final Clock clock;

	public ReportController(Reports reports, Clock clock) {
		this.reports = reports;
		this.clock = clock;
	}

	@GetMapping("/reports")
	public String reports(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "30") int days,
			Model model) {
		int period = PERIODS.contains(days) ? days : 30;
		long account = user.accountId();
		OffsetDateTime now = OffsetDateTime.now(clock);
		OffsetDateTime from = LocalDate.now(clock).minusDays(period - 1L).atStartOfDay(clock.getZone()).toOffsetDateTime();
		List<Reports.Day> perDay = reports.perDay(account, Math.min(period, 30));
		model.addAttribute("days", period);
		model.addAttribute("periods", PERIODS);
		model.addAttribute("stats", reports.stats(account, from, now.plusMinutes(1)));
		model.addAttribute("perDay", perDay);
		model.addAttribute("busiest", perDay.stream().mapToLong(Reports.Day::enquiries).max().orElse(0));
		model.addAttribute("missed", reports.missed(account, from, 25));
		model.addAttribute("sold", reports.topBooked(account, from, 15));
		model.addAttribute("fastSeconds", Reports.FAST_SECONDS);
		return "parts/reports";
	}

}
