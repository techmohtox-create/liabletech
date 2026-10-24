package com.liabletech.parts.mechanic;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.parts.booking.Booking;
import com.liabletech.parts.booking.Bookings;
import com.liabletech.parts.enquiry.Enquiries;
import com.liabletech.parts.enquiry.Enquiry;
import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.text.Phones;

/** The garages that buy from the shop: who asks most and what they book. */
@Controller
@RequestMapping("/mechanics")
public class MechanicController {

	private final Mechanics mechanics;

	private final Enquiries enquiries;

	private final Bookings bookings;

	private final Clock clock;

	public MechanicController(Mechanics mechanics, Enquiries enquiries, Bookings bookings, Clock clock) {
		this.mechanics = mechanics;
		this.enquiries = enquiries;
		this.bookings = bookings;
		this.clock = clock;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String q, Model model) {
		model.addAttribute("q", q);
		model.addAttribute("mechanics", mechanics.list(user.accountId(), q, OffsetDateTime.now(clock).minusDays(30), 500));
		return "parts/mechanics";
	}

	@PostMapping
	public String create(@AuthenticationPrincipal CurrentUser user, @RequestParam String phone,
			@RequestParam(required = false) String name, @RequestParam(required = false) String garage,
			@RequestParam(required = false) String area, RedirectAttributes redirect) {
		String normalized = Phones.normalize(phone);
		if (normalized == null) {
			redirect.addFlashAttribute("error", "Enter a valid mobile number.");
			return "redirect:/mechanics";
		}
		Mechanic mechanic = mechanics.findOrCreate(user.accountId(), normalized, name, OffsetDateTime.now(clock));
		mechanics.update(user.accountId(), mechanic.id(), name != null && !name.isBlank() ? name : mechanic.name(), garage,
				area, mechanic.notes());
		return "redirect:/mechanics/" + mechanic.id();
	}

	@GetMapping("/{id}")
	public String mechanic(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		long account = user.accountId();
		model.addAttribute("mechanic", mechanics.get(account, id));
		model.addAttribute("enquiries", enquiries.list(account, List.of(Enquiry.NEW, Enquiry.QUOTED, Enquiry.BOOKED,
				Enquiry.NOT_AVAILABLE, Enquiry.CLOSED), id, 50));
		model.addAttribute("bookings", bookings.list(account, List.of(Booking.BOOKED, Booking.HANDED_OVER, Booking.CANCELLED),
				id, 50));
		return "parts/mechanic";
	}

	@PostMapping("/{id}")
	public String update(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(required = false) String name, @RequestParam(required = false) String garage,
			@RequestParam(required = false) String area, @RequestParam(required = false) String notes,
			RedirectAttributes redirect) {
		mechanics.get(user.accountId(), id);
		mechanics.update(user.accountId(), id, name, garage, area, notes);
		redirect.addFlashAttribute("notice", "Saved.");
		return "redirect:/mechanics/" + id;
	}

	@PostMapping("/{id}/block")
	public String block(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(defaultValue = "false") boolean blocked) {
		mechanics.get(user.accountId(), id);
		mechanics.setBlocked(user.accountId(), id, blocked);
		return "redirect:/mechanics/" + id;
	}

}
