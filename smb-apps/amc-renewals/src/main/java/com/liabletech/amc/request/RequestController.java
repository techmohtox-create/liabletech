package com.liabletech.amc.request;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

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

import com.liabletech.amc.plan.Plans;
import com.liabletech.amc.team.Technicians;
import com.liabletech.platform.security.CurrentUser;

/** What customers asked for, from the link or on WhatsApp, and the visits booked. */
@Controller
@RequestMapping("/requests")
public class RequestController {

	private final ServiceRequests requests;

	private final RequestService service;

	private final Technicians technicians;

	private final Plans plans;

	private final Clock clock;

	public RequestController(ServiceRequests requests, RequestService service, Technicians technicians, Plans plans,
			Clock clock) {
		this.requests = requests;
		this.service = service;
		this.technicians = technicians;
		this.plans = plans;
		this.clock = clock;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "open") String show,
			Model model) {
		model.addAttribute("show", show);
		model.addAttribute("requests", requests.list(user.accountId(),
				"done".equals(show) ? List.of(ServiceRequest.DONE, ServiceRequest.DISMISSED)
						: List.of(ServiceRequest.NEW, ServiceRequest.SCHEDULED), 300));
		model.addAttribute("technicians", technicians.all(user.accountId()).stream().filter((t) -> t.active()).toList());
		model.addAttribute("plans", plans.all(user.accountId()).stream().filter((p) -> p.active()).toList());
		model.addAttribute("today", LocalDate.now(clock));
		return "amc/requests";
	}

	@PostMapping("/{id}/schedule")
	public String schedule(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on,
			@RequestParam(required = false) Long technicianId, RedirectAttributes redirect) {
		service.schedule(user.accountId(), id, on, technicianId);
		redirect.addFlashAttribute("notice", "Visit booked; the customer was told on WhatsApp.");
		return "redirect:/requests";
	}

	@PostMapping("/{id}/done")
	public String done(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
		service.done(user.accountId(), id);
		return "redirect:/requests";
	}

	@PostMapping("/{id}/dismiss")
	public String dismiss(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
		service.dismiss(user.accountId(), id);
		return "redirect:/requests";
	}

	@PostMapping("/{id}/renew")
	public String renew(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(required = false) Long planId, @RequestParam(required = false) BigDecimal price,
			@RequestParam(defaultValue = "false") boolean paid, @RequestParam(defaultValue = "UPI") String paymentMode,
			RedirectAttributes redirect) {
		try {
			long renewed = service.renew(user.accountId(), id, planId, price, paid, paymentMode);
			redirect.addFlashAttribute("notice", "Renewed. The customer was thanked on WhatsApp.");
			return "redirect:/contracts/" + renewed;
		}
		catch (IllegalStateException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
			return "redirect:/requests";
		}
	}

}
