package com.liabletech.parts.booking;

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

import com.liabletech.parts.enquiry.EnquiryService;
import com.liabletech.platform.security.CurrentUser;

/** Parts mechanics booked: set aside on the shelf until they are collected. */
@Controller
@RequestMapping("/bookings")
public class BookingController {

	static final List<String> PAYMENT_MODES = List.of("CASH", "UPI", "CREDIT");

	private final Bookings bookings;

	private final EnquiryService service;

	public BookingController(Bookings bookings, EnquiryService service) {
		this.bookings = bookings;
		this.service = service;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "open") String show,
			Model model) {
		boolean done = "done".equals(show);
		model.addAttribute("show", done ? "done" : "open");
		model.addAttribute("bookings", bookings.list(user.accountId(),
				done ? List.of(Booking.HANDED_OVER, Booking.CANCELLED) : List.of(Booking.BOOKED), null, 200));
		model.addAttribute("modes", PAYMENT_MODES);
		return "parts/bookings";
	}

	@PostMapping("/{id}/handed-over")
	public String handedOver(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(defaultValue = "CASH") String paymentMode, RedirectAttributes redirect) {
		service.handOver(user.accountId(), id, PAYMENT_MODES.contains(paymentMode) ? paymentMode : "CASH");
		redirect.addFlashAttribute("notice", "Handed over.");
		return "redirect:/bookings";
	}

	@PostMapping("/{id}/cancel")
	public String cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, RedirectAttributes redirect) {
		service.cancelBooking(user.accountId(), id);
		redirect.addFlashAttribute("notice", "Booking cancelled; the part is back in stock.");
		return "redirect:/bookings";
	}

}
