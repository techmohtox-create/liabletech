package com.liabletech.materials.supplier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.materials.catalog.Material;
import com.liabletech.materials.catalog.Materials;
import com.liabletech.materials.catalog.Unit;
import com.liabletech.materials.leakage.Flags;
import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.text.Phones;

@Controller
@RequestMapping("/suppliers")
public class SupplierController {

	private final Suppliers suppliers;

	private final Materials materials;

	private final Flags flags;

	private final Clock clock;

	public SupplierController(Suppliers suppliers, Materials materials, Flags flags, Clock clock) {
		this.suppliers = suppliers;
		this.materials = materials;
		this.flags = flags;
		this.clock = clock;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, Model model) {
		model.addAttribute("suppliers", suppliers.all(user.accountId()));
		model.addAttribute("bySupplier", flags.bySupplier(user.accountId()));
		return "materials/suppliers";
	}

	@PostMapping
	public String create(@AuthenticationPrincipal CurrentUser user, @RequestParam String name,
			@RequestParam(required = false) String phone, @RequestParam(required = false) String email,
			@RequestParam(required = false) String gstin, RedirectAttributes redirect) {
		if (name.isBlank()) {
			redirect.addFlashAttribute("error", "Give the supplier's name.");
			return "redirect:/suppliers";
		}
		long id = suppliers.create(user.accountId(), name, normalizePhone(phone), email, gstin);
		redirect.addFlashAttribute("notice", "Supplier added. Add the rates you agreed with them.");
		return "redirect:/suppliers/" + id;
	}

	@GetMapping("/{id}")
	public String detail(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		model.addAttribute("supplier", suppliers.get(user.accountId(), id));
		model.addAttribute("rates", suppliers.rates(user.accountId(), id));
		List<Material> all = materials.all(user.accountId());
		model.addAttribute("materials", all);
		model.addAttribute("materialNames", all.stream().collect(Collectors.toMap(Material::id, Material::name)));
		model.addAttribute("units", Unit.values());
		model.addAttribute("today", LocalDate.now(clock));
		model.addAttribute("flags", flags.list(user.accountId(), "ALL", null, id, 50));
		return "materials/supplier";
	}

	@PostMapping("/{id}")
	public String update(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam String name,
			@RequestParam(required = false) String phone, @RequestParam(required = false) String email,
			@RequestParam(required = false) String gstin, RedirectAttributes redirect) {
		suppliers.get(user.accountId(), id);
		suppliers.update(user.accountId(), id, name, normalizePhone(phone), email, gstin);
		redirect.addFlashAttribute("notice", "Saved.");
		return "redirect:/suppliers/" + id;
	}

	@PostMapping("/{id}/rates")
	public String addRate(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam long materialId,
			@RequestParam BigDecimal rate, @RequestParam Unit unit,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validTo,
			RedirectAttributes redirect) {
		suppliers.get(user.accountId(), id);
		materials.get(user.accountId(), materialId);
		if (rate.signum() <= 0) {
			redirect.addFlashAttribute("error", "Enter the agreed rate.");
		}
		else {
			suppliers.addRate(user.accountId(), id, materialId, rate, unit, validFrom, validTo);
		}
		return "redirect:/suppliers/" + id;
	}

	@PostMapping("/{id}/rates/{rateId}/delete")
	public String deleteRate(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @PathVariable long rateId) {
		suppliers.deleteRate(user.accountId(), rateId);
		return "redirect:/suppliers/" + id;
	}

	private static String normalizePhone(String phone) {
		String normalized = Phones.normalize(phone);
		return normalized != null ? normalized : phone;
	}

}
