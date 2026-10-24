package com.liabletech.exportdocs.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.liabletech.exportdocs.check.Findings;
import com.liabletech.exportdocs.shipment.Shipment;
import com.liabletech.exportdocs.shipment.Shipments;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.account.PlanCatalog;
import com.liabletech.platform.security.CurrentUser;

@Controller
public class DashboardController {

	private final Shipments shipments;

	private final Findings findings;

	private final Accounts accounts;

	private final PlanCatalog plans;

	private final Clock clock;

	public DashboardController(Shipments shipments, Findings findings, Accounts accounts, PlanCatalog plans, Clock clock) {
		this.shipments = shipments;
		this.findings = findings;
		this.accounts = accounts;
		this.plans = plans;
		this.clock = clock;
	}

	@GetMapping("/")
	public String dashboard(@AuthenticationPrincipal CurrentUser user, Model model) {
		LocalDate today = LocalDate.now(clock);
		List<Shipment> active = shipments.list(user.accountId(), true, 200)
			.stream()
			.sorted(Comparator.comparing((Shipment s) -> s.nextDeadline(today) == null ? LocalDate.MAX
					: s.nextDeadline(today).date()))
			.toList();
		Shipment.Deadline next = active.stream()
			.map((s) -> s.nextDeadline(today))
			.filter(Objects::nonNull)
			.filter((d) -> !d.date().isBefore(today))
			.min(Comparator.comparing(Shipment.Deadline::date))
			.orElse(null);
		model.addAttribute("today", today);
		model.addAttribute("active", active);
		model.addAttribute("discrepancies", active.stream().mapToInt(Shipment::criticalCount).sum());
		model.addAttribute("toFix", active.stream().filter((s) -> s.criticalCount() > 0).count());
		model.addAttribute("ready", active.stream().filter((s) -> Shipment.READY.equals(s.status())).count());
		model.addAttribute("next", next);
		model.addAttribute("nextShipment", next == null ? null
				: active.stream().filter((s) -> next.equals(s.nextDeadline(today))).findFirst().orElse(null));
		model.addAttribute("caught", findings.fixedSince(user.accountId(),
				today.withDayOfMonth(1).atStartOfDay(clock.getZone()).toOffsetDateTime()));
		model.addAttribute("plan", plans.summarize(accounts.get(user.accountId())));
		model.addAttribute("recent", shipments.list(user.accountId(), false, 8)
			.stream()
			.filter((s) -> !s.active())
			.toList());
		return "export/dashboard";
	}

}
