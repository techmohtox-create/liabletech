package com.liabletech.amc.contract;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.amc.customer.Appliance;
import com.liabletech.amc.customer.Appliances;
import com.liabletech.amc.customer.Customers;
import com.liabletech.amc.plan.AmcPlan;
import com.liabletech.amc.plan.Plans;
import com.liabletech.amc.reminder.ReminderService;
import com.liabletech.amc.reminder.Reminders;
import com.liabletech.amc.request.RequestService;
import com.liabletech.amc.request.ServiceRequests;
import com.liabletech.platform.messaging.Outbox;
import com.liabletech.platform.security.CurrentUser;

@Controller
@RequestMapping("/contracts")
public class ContractController {

	private static final List<String> FILTERS = List.of("DUE", "IN_FORCE", "LAPSED", "RENEWED", "CANCELLED", "ALL");

	private final Contracts contracts;

	private final ContractService service;

	private final Customers customers;

	private final Appliances appliances;

	private final Plans plans;

	private final Reminders reminders;

	private final ReminderService reminderService;

	private final ServiceRequests requests;

	private final RequestService requestService;

	private final Outbox outbox;

	private final Clock clock;

	public ContractController(Contracts contracts, ContractService service, Customers customers, Appliances appliances,
			Plans plans, Reminders reminders, ReminderService reminderService, ServiceRequests requests,
			RequestService requestService, Outbox outbox, Clock clock) {
		this.contracts = contracts;
		this.service = service;
		this.customers = customers;
		this.appliances = appliances;
		this.plans = plans;
		this.reminders = reminders;
		this.reminderService = reminderService;
		this.requests = requests;
		this.requestService = requestService;
		this.outbox = outbox;
		this.clock = clock;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "DUE") String show,
			@RequestParam(required = false) String q, Model model) {
		String filter = FILTERS.contains(show) ? show : "DUE";
		LocalDate today = LocalDate.now(clock);
		model.addAttribute("show", filter);
		model.addAttribute("q", q);
		model.addAttribute("today", today);
		model.addAttribute("contracts", contracts.list(user.accountId(), filter, today, q, 500));
		model.addAttribute("metrics", contracts.metrics(user.accountId(), today, clock.getZone()));
		return "amc/contracts";
	}

	@GetMapping("/{id}")
	public String detail(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		long account = user.accountId();
		Contract contract = contracts.get(account, id);
		Appliance appliance = appliances.get(account, contract.applianceId());
		LocalDate today = LocalDate.now(clock);
		model.addAttribute("contract", contract);
		model.addAttribute("customer", customers.get(account, contract.customerId()));
		model.addAttribute("appliance", appliance);
		model.addAttribute("plans", plans.forFamily(account, appliance.applianceKind().family()));
		model.addAttribute("renewalPrice", reminderService.renewalPrice(account, contract));
		model.addAttribute("link", reminderService.link(contract));
		model.addAttribute("reminders", reminders.forContract(id));
		model.addAttribute("visits", ReminderService.visitDates(contract));
		model.addAttribute("visitsDone", requests.visitsDone(id));
		model.addAttribute("renewedBy", contracts.find(account, contract.renewedById()).orElse(null));
		model.addAttribute("history", contracts.forCustomer(contract.customerId())
			.stream()
			.filter((c) -> c.applianceId().equals(contract.applianceId()))
			.toList());
		model.addAttribute("messages", outbox.about(account, "contract", id).reversed());
		model.addAttribute("today", today);
		return "amc/contract";
	}

	@PostMapping
	public String create(@AuthenticationPrincipal CurrentUser user, @RequestParam long customerId,
			@RequestParam long applianceId, @RequestParam long planId, @RequestParam(required = false) BigDecimal price,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsOn,
			@RequestParam(defaultValue = "false") boolean paid, @RequestParam(defaultValue = "CASH") String paymentMode,
			RedirectAttributes redirect) {
		long account = user.accountId();
		customers.get(account, customerId);
		Appliance appliance = appliances.get(account, applianceId);
		AmcPlan plan = plans.find(account, planId).orElseThrow();
		BigDecimal amount = price != null && price.signum() > 0 ? price : plan.price();
		long id = service.sell(account, appliance.customerId(), applianceId, plan, amount, startsOn, "OFFICE",
				paid ? amount : null, paid ? paymentMode : null);
		redirect.addFlashAttribute("notice", "AMC added. Renewal reminders are scheduled.");
		return "redirect:/contracts/" + id;
	}

	@PostMapping("/{id}/renew")
	public String renew(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(required = false) Long planId, @RequestParam(required = false) BigDecimal price,
			@RequestParam(defaultValue = "false") boolean paid, @RequestParam(defaultValue = "CASH") String paymentMode,
			RedirectAttributes redirect) {
		try {
			long renewed = service.renew(user.accountId(), id, planId, price, paid, paymentMode);
			Contract fresh = contracts.get(user.accountId(), renewed);
			requestService.thank(user.accountId(), renewed);
			redirect.addFlashAttribute("notice", "Renewed until " + fresh.endsOn() + ". The customer was thanked on WhatsApp.");
			return "redirect:/contracts/" + renewed;
		}
		catch (IllegalStateException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
			return "redirect:/contracts/" + id;
		}
	}

	@PostMapping("/{id}/payment")
	public String payment(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam BigDecimal amount,
			@RequestParam(defaultValue = "CASH") String mode, RedirectAttributes redirect) {
		service.recordPayment(user.accountId(), id, amount, mode);
		redirect.addFlashAttribute("notice", "Payment recorded.");
		return "redirect:/contracts/" + id;
	}

	@PostMapping("/{id}/remind")
	public String remind(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, RedirectAttributes redirect) {
		try {
			reminderService.remindNow(user.accountId(), id);
			redirect.addFlashAttribute("notice", "Reminder sent on WhatsApp.");
		}
		catch (IllegalStateException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/contracts/" + id;
	}

	@PostMapping("/{id}/cancel")
	public String cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, RedirectAttributes redirect) {
		service.cancel(user.accountId(), id);
		redirect.addFlashAttribute("notice", "Contract cancelled; no more reminders for it.");
		return "redirect:/contracts/" + id;
	}

}
