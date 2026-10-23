package com.liabletech.materials.catalog;

import java.math.BigDecimal;

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

@Controller
@RequestMapping("/materials")
public class CatalogController {

	private final Materials materials;

	public CatalogController(Materials materials) {
		this.materials = materials;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, Model model) {
		model.addAttribute("materials", materials.all(user.accountId()));
		model.addAttribute("units", Unit.values());
		return "materials/materials";
	}

	@PostMapping
	public String create(@AuthenticationPrincipal CurrentUser user, @RequestParam String name,
			@RequestParam String category, @RequestParam Unit unit, @RequestParam(required = false) String aliases,
			@RequestParam(required = false) BigDecimal bagKg, @RequestParam(required = false) Integer barDiameterMm,
			@RequestParam(defaultValue = "0") BigDecimal tolerancePct, RedirectAttributes redirect) {
		if (name.isBlank()) {
			redirect.addFlashAttribute("error", "Give the material a name.");
			return "redirect:/materials";
		}
		materials.create(user.accountId(), name, category.isBlank() ? "OTHER" : category.trim().toUpperCase(), unit,
				aliases == null || aliases.isBlank() ? null : aliases.trim(), bagKg, barDiameterMm, tolerancePct);
		redirect.addFlashAttribute("notice", name.trim() + " added.");
		return "redirect:/materials";
	}

	@PostMapping("/{id}")
	public String update(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam String name,
			@RequestParam(required = false) String aliases, @RequestParam BigDecimal tolerancePct,
			@RequestParam(defaultValue = "false") boolean active) {
		materials.get(user.accountId(), id);
		materials.update(user.accountId(), id, name, aliases == null || aliases.isBlank() ? null : aliases.trim(),
				tolerancePct, active);
		return "redirect:/materials";
	}

}
