package com.liabletech.freight.party;

import java.time.Clock;
import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.platform.security.CurrentUser;

@Controller
@RequestMapping("/parties")
public class PartyController {

	private final Parties parties;

	private final Clock clock;

	public PartyController(Parties parties, Clock clock) {
		this.parties = parties;
		this.clock = clock;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, Model model) {
		model.addAttribute("balances", parties.balances(user.accountId(), LocalDate.now(clock)));
		return "freight/parties";
	}

	@GetMapping("/new")
	public String newParty(Model model) {
		model.addAttribute("form", new PartyForm());
		model.addAttribute("partyId", null);
		return "freight/party-form";
	}

	@PostMapping
	public String create(@AuthenticationPrincipal CurrentUser user, @Valid @ModelAttribute("form") PartyForm form,
			BindingResult result, Model model, RedirectAttributes redirect) {
		if (result.hasErrors()) {
			model.addAttribute("partyId", null);
			return "freight/party-form";
		}
		parties.create(user.accountId(), form);
		redirect.addFlashAttribute("notice", form.getName().trim() + " added.");
		return "redirect:/parties";
	}

	@GetMapping("/{id}/edit")
	public String edit(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		model.addAttribute("form", PartyForm.of(parties.get(user.accountId(), id)));
		model.addAttribute("partyId", id);
		return "freight/party-form";
	}

	@PostMapping("/{id}")
	public String update(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@Valid @ModelAttribute("form") PartyForm form, BindingResult result, Model model,
			RedirectAttributes redirect) {
		parties.get(user.accountId(), id);
		if (result.hasErrors()) {
			model.addAttribute("partyId", id);
			return "freight/party-form";
		}
		parties.update(user.accountId(), id, form);
		redirect.addFlashAttribute("notice", "Saved.");
		return "redirect:/invoices?show=all&party=" + id;
	}

}
