package com.liabletech.parts.settings;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.parts.PartsNavigation;
import com.liabletech.parts.catalog.PartType;
import com.liabletech.parts.enquiry.Identification;
import com.liabletech.parts.enquiry.PartMessages;
import com.liabletech.parts.enquiry.QuoteOptions;
import com.liabletech.parts.voice.SpeechToText;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.ai.DocumentAi;
import com.liabletech.platform.security.CurrentUser;

/** How the counter answers, and what is switched on: automatic quotes, alerts, AI and voice notes. */
@Controller
@RequestMapping("/setup")
public class SetupController {

	private final ShopSettingsRepository settings;

	private final Accounts accounts;

	private final DocumentAi ai;

	private final SpeechToText speech;

	public SetupController(ShopSettingsRepository settings, Accounts accounts, DocumentAi ai, SpeechToText speech) {
		this.settings = settings;
		this.accounts = accounts;
		this.ai = ai;
		this.speech = speech;
	}

	@GetMapping
	public String setup(@AuthenticationPrincipal CurrentUser user, Model model) {
		Account account = accounts.get(user.accountId());
		ShopSettings config = settings.get(account.id());
		Identification example = new Identification(PartType.BRAKE_PAD, null, List.of(), "Maruti Suzuki", "Swift", 2019,
				"FRONT", 1, 0.9, null, Identification.PARSED, null);
		model.addAttribute("settings", config);
		model.addAttribute("pro", PartsNavigation.isPro(account));
		model.addAttribute("aiOn", ai.available());
		model.addAttribute("voiceOn", speech.available());
		model.addAttribute("ownNumber", account.whatsappPhoneNumberId() != null);
		model.addAttribute("ack", PartMessages.acknowledge(account, example));
		model.addAttribute("quote", PartMessages.quote(account, example,
				List.of(new QuoteOptions.NewOption(null, "ProStop front brake pad set", new BigDecimal("1150")),
						new QuoteOptions.NewOption(null, "Genuine front brake pad set", new BigDecimal("1890"))),
				1, config.readyMinutes()));
		return "parts/setup";
	}

	@PostMapping
	public String save(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "false") boolean acknowledge,
			@RequestParam(defaultValue = "false") boolean autoQuote, @RequestParam(defaultValue = "80") int confidence,
			@RequestParam(defaultValue = "3") int nudgeMinutes, @RequestParam(defaultValue = "15") int readyMinutes,
			RedirectAttributes redirect) {
		// Off Pro the box is greyed out and not sent: keep what was chosen for when Pro is turned on.
		boolean automatic = PartsNavigation.isPro(accounts.get(user.accountId())) ? autoQuote
				: settings.get(user.accountId()).autoQuote();
		settings.save(new ShopSettings(user.accountId(), acknowledge, automatic,
				BigDecimal.valueOf(Math.max(50, Math.min(100, confidence))).movePointLeft(2),
				Math.max(0, Math.min(60, nudgeMinutes)), Math.max(1, Math.min(240, readyMinutes))));
		redirect.addFlashAttribute("notice", "Saved.");
		return "redirect:/setup";
	}

}
