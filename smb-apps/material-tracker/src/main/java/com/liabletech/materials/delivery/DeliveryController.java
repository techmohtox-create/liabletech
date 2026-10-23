package com.liabletech.materials.delivery;

import java.io.IOException;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.materials.catalog.Materials;
import com.liabletech.materials.catalog.Unit;
import com.liabletech.materials.leakage.Flag;
import com.liabletech.materials.leakage.Flags;
import com.liabletech.materials.site.Sites;
import com.liabletech.materials.supplier.Suppliers;
import com.liabletech.platform.ai.DocumentAi;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.security.CurrentUser;

@Controller
@RequestMapping("/deliveries")
public class DeliveryController {

	private final Deliveries deliveries;

	private final DeliveryService service;

	private final Sites sites;

	private final Suppliers suppliers;

	private final Materials materials;

	private final Flags flags;

	private final FileStore files;

	private final DocumentAi ai;

	public DeliveryController(Deliveries deliveries, DeliveryService service, Sites sites, Suppliers suppliers,
			Materials materials, Flags flags, FileStore files, DocumentAi ai) {
		this.deliveries = deliveries;
		this.service = service;
		this.sites = sites;
		this.suppliers = suppliers;
		this.materials = materials;
		this.flags = flags;
		this.files = files;
		this.ai = ai;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) Long site,
			@RequestParam(required = false) String status, Model model) {
		String filter = status == null || status.isBlank() ? null : status;
		model.addAttribute("site", site);
		model.addAttribute("status", filter);
		model.addAttribute("sites", sites.all(user.accountId()));
		model.addAttribute("deliveries", deliveries.list(user.accountId(), site, filter, 300));
		model.addAttribute("aiEnabled", ai.available());
		return "materials/deliveries";
	}

	@PostMapping("/upload")
	public String upload(@AuthenticationPrincipal CurrentUser user, @RequestParam long siteId,
			@RequestParam MultipartFile file, RedirectAttributes redirect) throws IOException {
		if (file.isEmpty()) {
			redirect.addFlashAttribute("error", "Choose a photo or PDF of the challan.");
			return "redirect:/deliveries";
		}
		StoredFile stored = files.save(user.accountId(), file.getBytes(), file.getContentType(), file.getOriginalFilename());
		DeliveryService.Received received = service.receive(user.accountId(), siteId, null, stored, null, "UPLOAD");
		if (received.isBill()) {
			redirect.addFlashAttribute("notice", "That photo is a bill, so it was added to Bills.");
			return "redirect:/bills/" + received.billId();
		}
		return "redirect:/deliveries/" + received.delivery().id();
	}

	@GetMapping("/{id}")
	public String detail(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		Delivery delivery = deliveries.get(user.accountId(), id);
		List<DeliveryLine> lines = deliveries.lines(id);
		DeliveryForm form = DeliveryForm.of(delivery, lines);
		for (int i = 0; i < 2; i++) {
			form.getLines().add(new DeliveryForm.Line());
		}
		model.addAttribute("delivery", delivery);
		model.addAttribute("form", form);
		model.addAttribute("file", delivery.fileId() == null ? null : files.get(delivery.fileId()));
		model.addAttribute("sites", sites.all(user.accountId()));
		model.addAttribute("suppliers", suppliers.all(user.accountId()));
		model.addAttribute("materials", materials.active(user.accountId()));
		model.addAttribute("units", Unit.values());
		model.addAttribute("flags", flags.forDelivery(user.accountId(), id));
		model.addAttribute("member",
				delivery.memberId() == null ? null : sites.member(user.accountId(), delivery.memberId()));
		return "materials/delivery";
	}

	@PostMapping("/{id}")
	public String save(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@ModelAttribute("form") DeliveryForm form, RedirectAttributes redirect) {
		List<Flag> shortages = service.saveFromOffice(user.accountId(), id, form);
		redirect.addFlashAttribute("notice", shortages.isEmpty() ? "Delivery confirmed: nothing short."
				: "Delivery confirmed with " + shortages.size() + (shortages.size() == 1 ? " shortage." : " shortages."));
		return "redirect:/deliveries/" + id;
	}

	@PostMapping("/{id}/reject")
	public String reject(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, RedirectAttributes redirect) {
		service.reject(user.accountId(), id);
		redirect.addFlashAttribute("notice", "Marked as not a delivery.");
		return "redirect:/deliveries";
	}

}
