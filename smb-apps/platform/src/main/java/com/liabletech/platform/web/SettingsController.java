package com.liabletech.platform.web;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.account.PlanCatalog;
import com.liabletech.platform.account.ProfileForm;
import com.liabletech.platform.account.SignupService;
import com.liabletech.platform.account.Users;
import com.liabletech.platform.ai.AiCalls;
import com.liabletech.platform.ai.AiProperties;
import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.whatsapp.WhatsAppProperties;

/** Business profile (printed on invoices), plan, team logins and the state of the integrations. */
@Controller
public class SettingsController {

	private final Accounts accounts;

	private final Users users;

	private final SignupService signups;

	private final PlanCatalog plans;

	private final AiProperties ai;

	private final AiCalls aiCalls;

	private final WhatsAppProperties whatsApp;

	private final Clock clock;

	public SettingsController(Accounts accounts, Users users, SignupService signups, PlanCatalog plans,
			AiProperties ai, AiCalls aiCalls, WhatsAppProperties whatsApp, Clock clock) {
		this.accounts = accounts;
		this.users = users;
		this.signups = signups;
		this.plans = plans;
		this.ai = ai;
		this.aiCalls = aiCalls;
		this.whatsApp = whatsApp;
		this.clock = clock;
	}

	@GetMapping("/settings")
	public String settings(@AuthenticationPrincipal CurrentUser user, Model model) {
		Account account = accounts.get(user.accountId());
		if (!model.containsAttribute("profile")) {
			model.addAttribute("profile", ProfileForm.of(account));
		}
		addOverview(user, account, model);
		return "platform/settings";
	}

	@PostMapping("/settings")
	public String saveProfile(@AuthenticationPrincipal CurrentUser user,
			@Valid @ModelAttribute("profile") ProfileForm profile, BindingResult result, Model model,
			RedirectAttributes redirect) {
		if (result.hasErrors()) {
			addOverview(user, accounts.get(user.accountId()), model);
			return "platform/settings";
		}
		accounts.updateProfile(user.accountId(), profile);
		redirect.addFlashAttribute("notice", "Business details saved.");
		return "redirect:/settings";
	}

	@PostMapping("/settings/whatsapp-number")
	public String saveWhatsAppNumber(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) String phoneNumberId, RedirectAttributes redirect) {
		accounts.setWhatsAppNumber(user.accountId(), phoneNumberId);
		redirect.addFlashAttribute("notice", "WhatsApp number saved.");
		return "redirect:/settings";
	}

	@PostMapping("/settings/team")
	public String addStaff(@AuthenticationPrincipal CurrentUser user, @RequestParam String name,
			@RequestParam String email, @RequestParam String password, RedirectAttributes redirect) {
		if (!user.isOwner()) {
			throw new NotFoundException("Page");
		}
		if (name.isBlank() || !email.contains("@") || password.length() < 8) {
			redirect.addFlashAttribute("error", "Give a name, an email and a password of at least 8 characters.");
		}
		else if (users.emailTaken(email)) {
			redirect.addFlashAttribute("error", email + " already has a login.");
		}
		else {
			signups.addStaff(user.accountId(), name, email, password);
			redirect.addFlashAttribute("notice", name + " can now sign in with " + email + ".");
		}
		return "redirect:/settings#team";
	}

	@PostMapping("/settings/plan")
	public String changePlan(@AuthenticationPrincipal CurrentUser user, @RequestParam String planCode,
			RedirectAttributes redirect) {
		if (!user.isOwner()) {
			throw new NotFoundException("Page");
		}
		plans.options()
			.stream()
			.filter((option) -> option.code().equals(planCode))
			.findFirst()
			.ifPresentOrElse((option) -> {
				accounts.setPlan(user.accountId(), option.code());
				redirect.addFlashAttribute("notice", "You are now on the " + option.name() + " plan.");
			}, () -> redirect.addFlashAttribute("error", "Unknown plan."));
		return "redirect:/settings";
	}

	@PostMapping("/settings/team/{userId}/active")
	public String setActive(@AuthenticationPrincipal CurrentUser user, @PathVariable long userId,
			@RequestParam boolean active) {
		if (!user.isOwner()) {
			throw new NotFoundException("Page");
		}
		users.setActive(user.accountId(), userId, active);
		return "redirect:/settings#team";
	}

	private void addOverview(CurrentUser user, Account account, Model model) {
		LocalDate monthStart = LocalDate.now(clock).withDayOfMonth(1);
		ZoneId zone = clock.getZone();
		model.addAttribute("account", account);
		model.addAttribute("plan", plans.summarize(account));
		model.addAttribute("planOptions", plans.options());
		model.addAttribute("team", users.forAccount(user.accountId()));
		model.addAttribute("aiEnabled", ai.enabled());
		model.addAttribute("aiModel", ai.model());
		model.addAttribute("aiUsage", aiCalls.usageSince(user.accountId(), monthStart.atStartOfDay(zone).toOffsetDateTime()));
		model.addAttribute("whatsApp", whatsApp);
	}

}
