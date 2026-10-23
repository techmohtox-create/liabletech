package com.liabletech.freight.dashboard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.liabletech.freight.invoice.Invoice;
import com.liabletech.freight.invoice.Invoices;
import com.liabletech.freight.party.Parties;
import com.liabletech.freight.pod.Pods;
import com.liabletech.freight.trip.Trips;
import com.liabletech.platform.security.CurrentUser;

/** Where the money is: owed, overdue, ready to bill, and who pays slowly. */
@Controller
public class DashboardController {

	private final Invoices invoices;

	private final Trips trips;

	private final Pods pods;

	private final Parties parties;

	private final Clock clock;

	public DashboardController(Invoices invoices, Trips trips, Pods pods, Parties parties, Clock clock) {
		this.invoices = invoices;
		this.trips = trips;
		this.pods = pods;
		this.parties = parties;
		this.clock = clock;
	}

	@GetMapping("/")
	public String dashboard(@AuthenticationPrincipal CurrentUser user, Model model) {
		long accountId = user.accountId();
		LocalDate today = LocalDate.now(clock);
		List<Invoice> open = invoices.open(accountId);
		BigDecimal outstanding = BigDecimal.ZERO;
		BigDecimal overdue = BigDecimal.ZERO;
		BigDecimal[] buckets = { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO };
		for (Invoice invoice : open) {
			BigDecimal balance = invoice.balance();
			outstanding = outstanding.add(balance);
			long late = ChronoUnit.DAYS.between(invoice.dueDate(), today);
			int bucket = late <= 0 ? 0 : late <= 30 ? 1 : late <= 60 ? 2 : late <= 90 ? 3 : 4;
			buckets[bucket] = buckets[bucket].add(balance);
			if (late > 0) {
				overdue = overdue.add(balance);
			}
		}
		BigDecimal largest = BigDecimal.ONE;
		for (BigDecimal amount : buckets) {
			largest = largest.max(amount);
		}
		String[] labels = { "Not yet due", "1–30 days late", "31–60 days late", "61–90 days late", "Over 90 days" };
		List<Bucket> ageing = new ArrayList<>();
		for (int i = 0; i < buckets.length; i++) {
			int percent = buckets[i].multiply(BigDecimal.valueOf(100)).divide(largest, 0, java.math.RoundingMode.HALF_UP).intValue();
			ageing.add(new Bucket(labels[i], buckets[i], percent, i == 0 ? "" : i == 1 ? "warn" : "bad"));
		}
		model.addAttribute("today", today);
		model.addAttribute("outstanding", outstanding);
		model.addAttribute("overdue", overdue);
		model.addAttribute("ageing", ageing);
		model.addAttribute("collected", invoices.collectedSince(accountId, today.withDayOfMonth(1)));
		model.addAttribute("podsToReview", pods.countNeedingReview(accountId));
		model.addAttribute("unbilled", trips.unbilled(accountId));
		model.addAttribute("inTransit", trips.countInTransit(accountId));
		model.addAttribute("dueToday", invoices.dueForFollowUp(accountId, today));
		model.addAttribute("parties", parties.balances(accountId, today).stream()
			.filter((p) -> p.outstanding().signum() > 0)
			.limit(6)
			.toList());
		model.addAttribute("recentPods", pods.list(accountId, null, 6));
		return "freight/dashboard";
	}

	public record Bucket(String label, BigDecimal amount, int percent, String tone) {
	}

}
