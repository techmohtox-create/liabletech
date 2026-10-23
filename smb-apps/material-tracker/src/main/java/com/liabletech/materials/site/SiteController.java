package com.liabletech.materials.site;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.materials.delivery.Deliveries;
import com.liabletech.materials.leakage.Flags;
import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.text.Phones;

@Controller
@RequestMapping("/sites")
public class SiteController {

	private final Sites sites;

	private final SiteLedger ledger;

	private final Deliveries deliveries;

	private final Flags flags;

	public SiteController(Sites sites, SiteLedger ledger, Deliveries deliveries, Flags flags) {
		this.sites = sites;
		this.ledger = ledger;
		this.deliveries = deliveries;
		this.flags = flags;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, Model model) {
		model.addAttribute("sites", sites.all(user.accountId()));
		model.addAttribute("members", sites.members(user.accountId()));
		model.addAttribute("bySite", flags.bySite(user.accountId()));
		return "materials/sites";
	}

	@GetMapping("/{id}")
	public String detail(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		Site site = sites.get(user.accountId(), id);
		model.addAttribute("siteRecord", site);
		model.addAttribute("ledger", ledger.forSite(user.accountId(), id));
		model.addAttribute("deliveries", deliveries.list(user.accountId(), id, null, 15));
		model.addAttribute("flags", flags.list(user.accountId(), "AT_RISK", id, null, 50));
		return "materials/site";
	}

	@PostMapping
	public String create(@AuthenticationPrincipal CurrentUser user, @RequestParam String name,
			@RequestParam(required = false) String location, RedirectAttributes redirect) {
		if (name.isBlank()) {
			redirect.addFlashAttribute("error", "Give the site a name.");
		}
		else {
			sites.create(user.accountId(), name, location);
			redirect.addFlashAttribute("notice", name.trim() + " added. Now add its supervisor's WhatsApp number.");
		}
		return "redirect:/sites";
	}

	@PostMapping("/{id}/active")
	public String active(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam boolean active) {
		sites.get(user.accountId(), id);
		sites.setActive(user.accountId(), id, active);
		return "redirect:/sites";
	}

	@PostMapping("/members")
	public String addMember(@AuthenticationPrincipal CurrentUser user, @RequestParam long siteId, @RequestParam String name,
			@RequestParam String phone, @RequestParam(defaultValue = SiteMember.SUPERVISOR) String role,
			RedirectAttributes redirect) {
		sites.get(user.accountId(), siteId);
		String normalized = Phones.normalize(phone);
		if (name.isBlank() || normalized == null) {
			redirect.addFlashAttribute("error", "Give a name and a valid WhatsApp number.");
		}
		else if (sites.memberByPhone(normalized).isPresent()) {
			redirect.addFlashAttribute("error", Phones.pretty(normalized) + " is already registered.");
		}
		else {
			sites.addMember(user.accountId(), siteId, name, normalized,
					SiteMember.STOREKEEPER.equals(role) ? SiteMember.STOREKEEPER : SiteMember.SUPERVISOR);
			redirect.addFlashAttribute("notice", name.trim() + " can now send delivery slips on WhatsApp.");
		}
		return "redirect:/sites";
	}

	@PostMapping("/members/{id}")
	public String updateMember(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam long siteId,
			@RequestParam(defaultValue = "false") boolean active) {
		sites.member(user.accountId(), id);
		sites.get(user.accountId(), siteId);
		sites.updateMember(user.accountId(), id, siteId, active);
		return "redirect:/sites";
	}

}
