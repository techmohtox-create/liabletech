package com.liabletech.freight.chase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.freight.invoice.GstMode;
import com.liabletech.freight.invoice.Invoices;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.security.CurrentUser;

/** The follow-up rules, what is due today, and a button to send it now. */
@Controller
@RequestMapping("/chasing")
public class ChaseController {

	private final ChaseSettingsRepository settings;

	private final ChaseService chase;

	private final Invoices invoices;

	private final Clock clock;

	public ChaseController(ChaseSettingsRepository settings, ChaseService chase, Invoices invoices, Clock clock) {
		this.settings = settings;
		this.chase = chase;
		this.invoices = invoices;
		this.clock = clock;
	}

	@GetMapping
	public String page(@AuthenticationPrincipal CurrentUser user, Model model) {
		LocalDate today = LocalDate.now(clock);
		model.addAttribute("settings", settings.get(user.accountId()));
		model.addAttribute("due", invoices.dueForFollowUp(user.accountId(), today));
		model.addAttribute("gstModes", GstMode.values());
		model.addAttribute("today", today);
		return "freight/chasing";
	}

	@PostMapping
	public String save(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "false") boolean enabled,
			@RequestParam String reminderDays, @RequestParam int repeatEveryDays,
			@RequestParam(defaultValue = "false") boolean sendWhatsapp,
			@RequestParam(defaultValue = "false") boolean sendEmail, @RequestParam GstMode defaultGstMode,
			@RequestParam BigDecimal defaultGstRate, @RequestParam String invoicePrefix,
			@RequestParam(required = false) String invoiceTerms, @RequestParam(defaultValue = "false") boolean ownerDigest,
			RedirectAttributes redirect) {
		ChaseSettings candidate = new ChaseSettings(user.accountId(), enabled, reminderDays, repeatEveryDays, sendWhatsapp,
				sendEmail, defaultGstMode.name(), defaultGstRate, invoicePrefix, invoiceTerms, ownerDigest);
		if (candidate.offsets().isEmpty() || repeatEveryDays < 1 || repeatEveryDays > 90
				|| !invoicePrefix.matches("[A-Za-z0-9]{1,10}") || defaultGstRate.signum() < 0
				|| defaultGstRate.compareTo(BigDecimal.valueOf(28)) > 0) {
			redirect.addFlashAttribute("error",
					"Check the values: reminder days like -2,0,7,15; repeat every 1 to 90 days; a short letters-and-digits prefix.");
			return "redirect:/chasing";
		}
		String days = String.join(",", candidate.offsets().stream().map(String::valueOf).toList());
		settings.save(new ChaseSettings(user.accountId(), enabled, days, repeatEveryDays, sendWhatsapp, sendEmail,
				defaultGstMode.name(), defaultGstRate, invoicePrefix.toUpperCase(java.util.Locale.ROOT),
				invoiceTerms == null || invoiceTerms.isBlank() ? null : invoiceTerms.trim(), ownerDigest));
		redirect.addFlashAttribute("notice", "Follow-up rules saved.");
		return "redirect:/chasing";
	}

	@PostMapping("/run")
	public String run(@AuthenticationPrincipal CurrentUser user, RedirectAttributes redirect) {
		int count = chase.runFor(user.accountId());
		redirect.addFlashAttribute("notice",
				count == 0 ? "No reminders were due." : count + (count == 1 ? " reminder sent." : " reminders sent."));
		return "redirect:/chasing";
	}

	@PostMapping("/digest")
	public String digest(@AuthenticationPrincipal CurrentUser user, RedirectAttributes redirect) {
		OutboundMessage message = chase.digest(user.accountId());
		redirect.addFlashAttribute(message == null ? "error" : "notice",
				message == null ? "Add your mobile number in Settings to get the daily summary."
						: "Today's summary sent to your WhatsApp.");
		return "redirect:/chasing";
	}

}
