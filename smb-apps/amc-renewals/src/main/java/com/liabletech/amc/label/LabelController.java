package com.liabletech.amc.label;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.amc.customer.Appliance;
import com.liabletech.amc.customer.ApplianceKind;
import com.liabletech.amc.customer.Appliances;
import com.liabletech.amc.customer.Customers;
import com.liabletech.amc.plan.AmcPlan;
import com.liabletech.amc.plan.Plans;
import com.liabletech.platform.ai.DocumentAi;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.security.CurrentUser;

/** Label photos from technicians: the ones that need the office to finish them. */
@Controller
@RequestMapping("/labels")
public class LabelController {

	private final LabelScans scans;

	private final LabelService service;

	private final Customers customers;

	private final Appliances appliances;

	private final Plans plans;

	private final FileStore files;

	private final DocumentAi ai;

	public LabelController(LabelScans scans, LabelService service, Customers customers, Appliances appliances, Plans plans,
			FileStore files, DocumentAi ai) {
		this.scans = scans;
		this.service = service;
		this.customers = customers;
		this.appliances = appliances;
		this.plans = plans;
		this.files = files;
		this.ai = ai;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "review") String show,
			Model model) {
		model.addAttribute("show", show);
		model.addAttribute("scans", scans.list(user.accountId(), "all".equals(show) ? null : LabelScan.NEEDS_REVIEW, 300));
		model.addAttribute("aiEnabled", ai.available());
		return "amc/labels";
	}

	@GetMapping("/{id}")
	public String review(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		long account = user.accountId();
		LabelScan scan = scans.get(account, id);
		CaptionParser.Caption caption = CaptionParser.parse(scan.caption());
		ApplianceKind kind = ApplianceKind.parse(scan.kind());
		Long planId = caption.amcSold() ? service.suggestPlan(account, kind, caption).map(AmcPlan::id).orElse(null) : null;
		Appliance saved = appliances.find(account, scan.applianceId()).orElse(null);
		model.addAttribute("scan", scan);
		model.addAttribute("form", saved != null ? LabelForm.of(saved, customers.get(account, saved.customerId()))
				: LabelForm.of(scan, caption, planId));
		model.addAttribute("file", files.get(scan.fileId()));
		model.addAttribute("kinds", ApplianceKind.values());
		model.addAttribute("plans", plans.all(account).stream().filter(AmcPlan::active).toList());
		model.addAttribute("existing", customers.byPhone(account, scan.customerPhone()).orElse(null));
		model.addAttribute("appliance", saved);
		return "amc/label";
	}

	@PostMapping("/{id}")
	public String link(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @ModelAttribute("form") LabelForm form,
			RedirectAttributes redirect) {
		try {
			LabelService.Linked linked = service.link(user.accountId(), id, form);
			redirect.addFlashAttribute("notice", linked.contractId() != null ? "Saved, with the AMC. Reminders are scheduled."
					: "Appliance saved to the customer.");
			return "redirect:/customers/" + linked.customerId();
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
			return "redirect:/labels/" + id;
		}
	}

	@PostMapping("/{id}/reject")
	public String reject(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, RedirectAttributes redirect) {
		scans.reject(user.accountId(), id);
		redirect.addFlashAttribute("notice", "Photo set aside.");
		return "redirect:/labels";
	}

}
