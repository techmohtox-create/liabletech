package com.liabletech.freight.trip;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.freight.fleet.Fleet;
import com.liabletech.freight.party.Parties;
import com.liabletech.freight.pod.Pods;
import com.liabletech.platform.security.CurrentUser;

@Controller
@RequestMapping("/trips")
public class TripController {

	private final Trips trips;

	private final Parties parties;

	private final Fleet fleet;

	private final Pods pods;

	private final Clock clock;

	public TripController(Trips trips, Parties parties, Fleet fleet, Pods pods, Clock clock) {
		this.trips = trips;
		this.parties = parties;
		this.fleet = fleet;
		this.pods = pods;
		this.clock = clock;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String status,
			Model model) {
		String filter = status == null || status.isBlank() ? null : status;
		model.addAttribute("status", filter);
		model.addAttribute("trips", trips.list(user.accountId(), filter, 300));
		model.addAttribute("unbilled", trips.unbilled(user.accountId()));
		return "freight/trips";
	}

	@GetMapping("/new")
	public String newTrip(@AuthenticationPrincipal CurrentUser user, Model model) {
		TripForm form = new TripForm();
		form.setLrDate(LocalDate.now(clock));
		return form(user, model, form, null);
	}

	@PostMapping
	public String create(@AuthenticationPrincipal CurrentUser user, @Valid @ModelAttribute("form") TripForm form,
			BindingResult result, Model model, RedirectAttributes redirect) {
		if (result.hasErrors()) {
			return form(user, model, form, null);
		}
		checkOwnership(user.accountId(), form);
		long id = trips.create(user.accountId(), form, form.getDeliveredOn() != null ? Trip.DELIVERED : Trip.IN_TRANSIT);
		redirect.addFlashAttribute("notice", "Trip saved. When the driver sends the POD, it is matched by LR number.");
		return "redirect:/trips/" + id;
	}

	@GetMapping("/{id}")
	public String detail(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		Trip trip = trips.get(user.accountId(), id);
		model.addAttribute("trip", trip);
		model.addAttribute("party", trip.partyId() == null ? null : parties.get(user.accountId(), trip.partyId()));
		model.addAttribute("vehicle", fleet.vehicle(user.accountId(), trip.vehicleId()).orElse(null));
		model.addAttribute("driver", trip.driverId() == null ? null : fleet.driver(user.accountId(), trip.driverId()));
		model.addAttribute("pods", pods.ofTrip(user.accountId(), id));
		return "freight/trip";
	}

	@GetMapping("/{id}/edit")
	public String edit(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		return form(user, model, TripForm.of(trips.get(user.accountId(), id)), id);
	}

	@PostMapping("/{id}")
	public String update(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@Valid @ModelAttribute("form") TripForm form, BindingResult result, Model model,
			RedirectAttributes redirect) {
		Trip trip = trips.get(user.accountId(), id);
		if (result.hasErrors()) {
			return form(user, model, form, id);
		}
		checkOwnership(user.accountId(), form);
		trips.update(user.accountId(), trip.id(), form);
		if (Trip.IN_TRANSIT.equals(trip.status()) && form.getDeliveredOn() != null) {
			trips.markDelivered(user.accountId(), trip.id(), form.getDeliveredOn());
		}
		redirect.addFlashAttribute("notice", "Trip updated.");
		return "redirect:/trips/" + id;
	}

	@PostMapping("/{id}/cancel")
	public String cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, RedirectAttributes redirect) {
		Trip trip = trips.get(user.accountId(), id);
		if (trip.invoiceId() != null) {
			redirect.addFlashAttribute("error", "This trip is already invoiced.");
		}
		else {
			trips.setStatus(user.accountId(), id, Trip.CANCELLED);
			redirect.addFlashAttribute("notice", "Trip cancelled.");
		}
		return "redirect:/trips/" + id;
	}

	private String form(CurrentUser user, Model model, TripForm form, Long id) {
		model.addAttribute("form", form);
		model.addAttribute("tripId", id);
		model.addAttribute("parties", parties.all(user.accountId()));
		model.addAttribute("vehicles", fleet.vehicles(user.accountId()));
		model.addAttribute("drivers", fleet.drivers(user.accountId()));
		return "freight/trip-form";
	}

	/** Ids in the form must belong to this fleet; otherwise they are dropped. */
	private void checkOwnership(long accountId, TripForm form) {
		if (form.getPartyId() != null) {
			parties.get(accountId, form.getPartyId());
		}
		if (form.getVehicleId() != null && fleet.vehicle(accountId, form.getVehicleId()).isEmpty()) {
			form.setVehicleId(null);
		}
		if (form.getDriverId() != null) {
			fleet.driver(accountId, form.getDriverId());
		}
	}

}
