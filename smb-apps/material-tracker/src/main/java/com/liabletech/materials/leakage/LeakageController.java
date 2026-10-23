package com.liabletech.materials.leakage;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.liabletech.materials.site.Sites;
import com.liabletech.materials.supplier.Suppliers;
import com.liabletech.platform.security.CurrentUser;

/** Every finding across sites and suppliers, and their outcome. */
@Controller
@RequestMapping("/leakage")
public class LeakageController {

	private final Flags flags;

	private final Sites sites;

	private final Suppliers suppliers;

	private final Clock clock;

	public LeakageController(Flags flags, Sites sites, Suppliers suppliers, Clock clock) {
		this.flags = flags;
		this.sites = sites;
		this.suppliers = suppliers;
		this.clock = clock;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "AT_RISK") String status,
			@RequestParam(required = false) Long site, @RequestParam(required = false) Long supplier, Model model) {
		String filter = List.of("AT_RISK", "OPEN", "RAISED", "RECOVERED", "DISMISSED", "CLOSED", "ALL").contains(status)
				? status : "AT_RISK";
		model.addAttribute("status", filter);
		model.addAttribute("site", site);
		model.addAttribute("supplier", supplier);
		model.addAttribute("sites", sites.all(user.accountId()));
		model.addAttribute("suppliers", suppliers.all(user.accountId()));
		model.addAttribute("flags", flags.list(user.accountId(), filter, site, supplier, 500));
		return "materials/leakage";
	}

	@PostMapping("/{id}")
	public String update(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam String status,
			@RequestParam(required = false) String note, @RequestParam(defaultValue = "/leakage") String back) {
		Flag flag = flags.get(user.accountId(), id);
		if (List.of(Flag.DISMISSED, Flag.RECOVERED, Flag.OPEN).contains(status)) {
			flags.setStatus(user.accountId(), flag.id(), status, note == null || note.isBlank() ? null : note.trim(),
					OffsetDateTime.now(clock));
		}
		return "redirect:" + (back.startsWith("/") && !back.startsWith("//") ? back : "/leakage");
	}

}
