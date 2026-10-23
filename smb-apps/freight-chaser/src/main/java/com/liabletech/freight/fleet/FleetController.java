package com.liabletech.freight.fleet;

import org.springframework.dao.DuplicateKeyException;
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

@Controller
@RequestMapping("/fleet")
public class FleetController {

	private final Fleet fleet;

	public FleetController(Fleet fleet) {
		this.fleet = fleet;
	}

	@GetMapping
	public String fleet(@AuthenticationPrincipal CurrentUser user, Model model) {
		model.addAttribute("vehicles", fleet.vehicles(user.accountId()));
		model.addAttribute("drivers", fleet.drivers(user.accountId()));
		return "freight/fleet";
	}

	@PostMapping("/vehicles")
	public String addVehicle(@AuthenticationPrincipal CurrentUser user, @RequestParam String registration,
			@RequestParam(required = false) String description, RedirectAttributes redirect) {
		String normalized = Vehicle.normalize(registration);
		if (normalized == null || normalized.length() < 6) {
			redirect.addFlashAttribute("error", "Enter the full registration number, e.g. MH12AB1234.");
			return "redirect:/fleet";
		}
		try {
			fleet.addVehicle(user.accountId(), normalized, description);
			redirect.addFlashAttribute("notice", normalized + " added.");
		}
		catch (DuplicateKeyException ex) {
			redirect.addFlashAttribute("error", normalized + " is already in your fleet.");
		}
		return "redirect:/fleet";
	}

	@PostMapping("/vehicles/{id}/active")
	public String vehicleActive(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam boolean active) {
		fleet.setVehicleActive(user.accountId(), id, active);
		return "redirect:/fleet";
	}

	@PostMapping("/drivers")
	public String addDriver(@AuthenticationPrincipal CurrentUser user, @RequestParam String name,
			@RequestParam String phone, @RequestParam(required = false) Long vehicleId, RedirectAttributes redirect) {
		String normalized = Phones.normalize(phone);
		if (name.isBlank() || normalized == null) {
			redirect.addFlashAttribute("error", "Give the driver's name and a valid WhatsApp number.");
			return "redirect:/fleet";
		}
		if (fleet.driverByPhone(normalized).isPresent()) {
			redirect.addFlashAttribute("error",
					Phones.pretty(normalized) + " is already registered as a driver. Each number can belong to one fleet.");
			return "redirect:/fleet";
		}
		fleet.addDriver(user.accountId(), name, normalized, fleet.vehicle(user.accountId(), vehicleId).map(Vehicle::id).orElse(null));
		redirect.addFlashAttribute("notice",
				name.trim() + " added. Photos they send on WhatsApp now arrive in your POD inbox.");
		return "redirect:/fleet";
	}

	@PostMapping("/drivers/{id}")
	public String updateDriver(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam String name, @RequestParam(required = false) Long vehicleId,
			@RequestParam(defaultValue = "false") boolean active) {
		Driver driver = fleet.driver(user.accountId(), id);
		fleet.updateDriver(user.accountId(), driver.id(), name.isBlank() ? driver.name() : name,
				fleet.vehicle(user.accountId(), vehicleId).map(Vehicle::id).orElse(null), active);
		return "redirect:/fleet";
	}

}
