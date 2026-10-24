package com.liabletech.amc.setup;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.amc.contract.Contract;
import com.liabletech.amc.customer.Appliance;
import com.liabletech.amc.customer.ApplianceKind;
import com.liabletech.amc.customer.Customer;
import com.liabletech.amc.plan.AmcPlan;
import com.liabletech.amc.plan.Plans;
import com.liabletech.amc.reminder.ReminderMessages;
import com.liabletech.amc.reminder.ReminderService;
import com.liabletech.amc.reminder.ReminderSettings;
import com.liabletech.amc.reminder.ReminderSettingsRepository;
import com.liabletech.amc.reminder.Reminders;
import com.liabletech.amc.team.Technicians;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.text.Phones;

/** The plans the business sells, its technicians, and when reminders go out. */
@Controller
@RequestMapping("/setup")
public class SetupController {

	static final List<String> FAMILIES = List.of("AC", "RO", "WATER_HEATER", "ANY");

	private final Plans plans;

	private final Technicians technicians;

	private final ReminderSettingsRepository settings;

	private final ReminderService reminderService;

	private final Reminders reminders;

	private final Accounts accounts;

	private final Clock clock;

	public SetupController(Plans plans, Technicians technicians, ReminderSettingsRepository settings,
			ReminderService reminderService, Reminders reminders, Accounts accounts, Clock clock) {
		this.plans = plans;
		this.technicians = technicians;
		this.settings = settings;
		this.reminderService = reminderService;
		this.reminders = reminders;
		this.accounts = accounts;
		this.clock = clock;
	}

	@GetMapping
	public String setup(@AuthenticationPrincipal CurrentUser user, Model model) {
		long accountId = user.accountId();
		Account account = accounts.get(accountId);
		ReminderSettings config = settings.get(accountId);
		List<AmcPlan> all = plans.all(accountId);
		model.addAttribute("account", account);
		model.addAttribute("plans", all);
		model.addAttribute("families", FAMILIES);
		model.addAttribute("technicians", technicians.all(accountId));
		model.addAttribute("settings", config);
		model.addAttribute("schedule", config.offsets().stream().map(SetupController::describe).toList());
		model.addAttribute("previews", previews(account, all));
		model.addAttribute("recent", reminders.recent(accountId, 15));
		return "amc/setup";
	}

	@PostMapping("/plans")
	public String addPlan(@AuthenticationPrincipal CurrentUser user, @RequestParam String name,
			@RequestParam(defaultValue = "AC") String family, @RequestParam BigDecimal price,
			@RequestParam(defaultValue = "12") int months, @RequestParam(defaultValue = "3") int visits,
			@RequestParam(required = false) String covers, RedirectAttributes redirect) {
		String problem = check(name, price, months, visits);
		if (problem != null) {
			redirect.addFlashAttribute("error", problem);
			return "redirect:/setup";
		}
		plans.create(user.accountId(), name, FAMILIES.contains(family) ? family : "ANY", price, months, visits, covers);
		redirect.addFlashAttribute("notice", "Plan added.");
		return "redirect:/setup";
	}

	@PostMapping("/plans/{id}")
	public String updatePlan(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam String name,
			@RequestParam BigDecimal price, @RequestParam int months, @RequestParam int visits,
			@RequestParam(required = false) String covers, @RequestParam(defaultValue = "false") boolean active,
			RedirectAttributes redirect) {
		plans.find(user.accountId(), id).orElseThrow();
		String problem = check(name, price, months, visits);
		if (problem != null) {
			redirect.addFlashAttribute("error", problem);
			return "redirect:/setup";
		}
		plans.update(user.accountId(), id, name, price, months, visits, covers, active);
		redirect.addFlashAttribute("notice", "Plan saved. Reminders quote the new price from now on.");
		return "redirect:/setup";
	}

	@PostMapping("/technicians")
	public String addTechnician(@AuthenticationPrincipal CurrentUser user, @RequestParam String name,
			@RequestParam String phone, RedirectAttributes redirect) {
		String normalized = Phones.normalize(phone);
		if (normalized == null || !StringUtils.hasText(name)) {
			redirect.addFlashAttribute("error", "Enter the technician's name and a valid WhatsApp number.");
			return "redirect:/setup";
		}
		try {
			technicians.add(user.accountId(), name, normalized);
			redirect.addFlashAttribute("notice", name.trim() + " can now send label photos on WhatsApp.");
		}
		catch (DuplicateKeyException ex) {
			redirect.addFlashAttribute("error", "That number is already registered as a technician.");
		}
		return "redirect:/setup";
	}

	@PostMapping("/technicians/{id}/active")
	public String technicianActive(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(defaultValue = "false") boolean active) {
		technicians.find(user.accountId(), id).orElseThrow();
		technicians.setActive(user.accountId(), id, active);
		return "redirect:/setup";
	}

	@PostMapping("/reminders")
	public String saveReminders(@AuthenticationPrincipal CurrentUser user, @RequestParam String renewalOffsets,
			@RequestParam(defaultValue = "false") boolean serviceReminders,
			@RequestParam(defaultValue = "false") boolean autoSend, RedirectAttributes redirect) {
		List<Integer> offsets = ReminderSettings.parse(renewalOffsets);
		if (offsets.isEmpty()) {
			redirect.addFlashAttribute("error", "Enter at least one day, e.g. -30, -7, 0, 7.");
			return "redirect:/setup";
		}
		String text = String.join(", ", offsets.stream().map(String::valueOf).toList());
		settings.save(new ReminderSettings(user.accountId(), text, serviceReminders, autoSend));
		redirect.addFlashAttribute("notice", "Reminder schedule saved.");
		return "redirect:/setup";
	}

	@PostMapping("/reminders/run")
	public String runNow(@AuthenticationPrincipal CurrentUser user, RedirectAttributes redirect) {
		int sent = reminderService.runFor(user.accountId());
		redirect.addFlashAttribute("notice", sent == 0 ? "Nothing was due today; every reminder has already gone out."
				: sent + (sent == 1 ? " reminder" : " reminders") + " sent. See them in the Outbox.");
		return "redirect:/setup";
	}

	/** "30 days before", "on the day", "7 days after". */
	static String describe(int offset) {
		if (offset == 0) {
			return "on the day it ends";
		}
		int days = Math.abs(offset);
		return days + (days == 1 ? " day " : " days ") + (offset < 0 ? "before" : "after");
	}

	private static String check(String name, BigDecimal price, int months, int visits) {
		if (!StringUtils.hasText(name)) {
			return "Give the plan a name.";
		}
		if (price == null || price.signum() <= 0) {
			return "Enter the plan's price.";
		}
		if (months < 1 || months > 60) {
			return "A plan runs between 1 and 60 months.";
		}
		if (visits < 0 || visits > 24) {
			return "Visits must be between 0 and 24.";
		}
		return null;
	}

	/** The messages customers get, with an example customer, so the owner sees exactly what goes out. */
	private Map<String, String> previews(Account account, List<AmcPlan> all) {
		LocalDate today = LocalDate.now(clock);
		AmcPlan plan = all.stream()
			.filter((p) -> p.active() && p.appliesTo("AC"))
			.findFirst()
			.orElse(new AmcPlan(0L, account.id(), "AC Care AMC", "AC", BigDecimal.valueOf(1499), 12, 3, null, true));
		Customer customer = new Customer(0L, account.id(), "Sunita Deshpande", "919800000000", null, null, false, null,
				OffsetDateTime.now(clock));
		Appliance appliance = new Appliance(0L, account.id(), 0L, ApplianceKind.SPLIT_AC.name(), "LG", null, null, "1.5 TR",
				null, null, "bedroom", null, OffsetDateTime.now(clock));
		String link = reminderService.link(example(account, plan, today.plusDays(15))).replace("/example", "/…");
		return Map.of("renewal",
				ReminderMessages.renewal(account, customer, appliance, example(account, plan, today.plusDays(15)),
						plan.price(), today, link),
				"lapsed",
				ReminderMessages.renewal(account, customer, appliance, example(account, plan, today.minusDays(7)),
						plan.price(), today, link),
				"service", ReminderMessages.serviceDue(account, customer, appliance, example(account, plan, today.plusDays(200)),
						2, link));
	}

	private Contract example(Account account, AmcPlan plan, LocalDate ends) {
		return new Contract(0L, account.id(), 0L, 0L, plan.id(), plan.name(), plan.price(), ends.minusYears(1).plusDays(1),
				ends, plan.visits(), Contract.ACTIVE, null, "example", null, null, null, "OFFICE", OffsetDateTime.now(clock));
	}

}
