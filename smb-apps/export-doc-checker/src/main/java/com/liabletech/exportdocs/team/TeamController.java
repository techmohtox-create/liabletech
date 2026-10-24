package com.liabletech.exportdocs.team;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.text.Phones;

/** Who may send documents on WhatsApp. */
@Controller
@RequestMapping("/team")
public class TeamController {

	private final Senders senders;

	public TeamController(Senders senders) {
		this.senders = senders;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, Model model) {
		model.addAttribute("senders", senders.all(user.accountId()));
		return "export/team";
	}

	@PostMapping
	public String add(@AuthenticationPrincipal CurrentUser user, @RequestParam String name, @RequestParam String phone,
			RedirectAttributes redirect) {
		String normalized = Phones.normalize(phone);
		if (name.isBlank() || normalized == null) {
			redirect.addFlashAttribute("error", "Give a name and a valid WhatsApp number.");
		}
		else if (senders.byPhone(normalized).isPresent()) {
			redirect.addFlashAttribute("error", Phones.pretty(normalized) + " is already registered.");
		}
		else {
			senders.add(user.accountId(), name, normalized);
			redirect.addFlashAttribute("notice", name.trim() + " can now send export documents on WhatsApp.");
		}
		return "redirect:/team";
	}

	@PostMapping("/{id}/active")
	public String active(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(defaultValue = "false") boolean active) {
		senders.setActive(user.accountId(), id, active);
		return "redirect:/team";
	}

}
