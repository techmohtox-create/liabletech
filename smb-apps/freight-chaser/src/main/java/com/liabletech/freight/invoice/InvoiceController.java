package com.liabletech.freight.invoice;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.liabletech.freight.chase.ChaseService;
import com.liabletech.freight.chase.ChaseSettings;
import com.liabletech.freight.chase.ChaseSettingsRepository;
import com.liabletech.freight.chase.FollowUps;
import com.liabletech.freight.party.Parties;
import com.liabletech.freight.party.Party;
import com.liabletech.freight.pod.Pods;
import com.liabletech.freight.trip.Trip;
import com.liabletech.freight.trip.Trips;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.messaging.Outbox;
import com.liabletech.platform.security.CurrentUser;

@Controller
@RequestMapping("/invoices")
public class InvoiceController {

	private final Invoices invoices;

	private final InvoiceService service;

	private final ChaseService chase;

	private final Trips trips;

	private final Pods pods;

	private final Parties parties;

	private final FollowUps followUps;

	private final Outbox outbox;

	private final ChaseSettingsRepository settings;

	private final Clock clock;

	public InvoiceController(Invoices invoices, InvoiceService service, ChaseService chase, Trips trips, Pods pods,
			Parties parties, FollowUps followUps, Outbox outbox, ChaseSettingsRepository settings, Clock clock) {
		this.invoices = invoices;
		this.service = service;
		this.chase = chase;
		this.trips = trips;
		this.pods = pods;
		this.parties = parties;
		this.followUps = followUps;
		this.outbox = outbox;
		this.settings = settings;
		this.clock = clock;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "open") String show,
			@RequestParam(required = false) Long party, Model model) {
		String filter = List.of("open", "overdue", "draft", "paid", "all").contains(show) ? show : "open";
		LocalDate today = LocalDate.now(clock);
		List<Invoices.InvoiceRow> rows = invoices.list(user.accountId(), filter, today, party);
		model.addAttribute("show", filter);
		model.addAttribute("party", party == null ? null : parties.get(user.accountId(), party));
		model.addAttribute("invoices", rows);
		model.addAttribute("today", today);
		model.addAttribute("totalBalance", rows.stream()
			.filter(Invoices.InvoiceRow::open)
			.map(Invoices.InvoiceRow::balance)
			.reduce(BigDecimal.ZERO, BigDecimal::add));
		return "freight/invoices";
	}

	/** The billing form for trips ticked on the trips page. */
	@GetMapping("/new")
	public String newInvoice(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(name = "trip", required = false) List<Long> tripIds, Model model, RedirectAttributes redirect) {
		List<Trip> selected = tripIds == null ? List.of() : trips.ofIds(user.accountId(), tripIds);
		if (selected.isEmpty()) {
			redirect.addFlashAttribute("error", "Tick one or more delivered trips to bill.");
			return "redirect:/trips?status=DELIVERED";
		}
		NewInvoiceForm form = new NewInvoiceForm();
		form.setTripIds(selected.stream().map(Trip::id).toList());
		form.setPartyId(selected.stream().map(Trip::partyId).filter(Objects::nonNull).findFirst().orElse(null));
		form.setInvoiceDate(LocalDate.now(clock));
		ChaseSettings chaseSettings = settings.get(user.accountId());
		form.setGstMode(chaseSettings.gstMode());
		form.setGstRate(chaseSettings.defaultGstRate());
		return form(user, model, form);
	}

	@PostMapping
	public String create(@AuthenticationPrincipal CurrentUser user, @Valid @ModelAttribute("form") NewInvoiceForm form,
			BindingResult result, Model model, RedirectAttributes redirect) {
		if (result.hasErrors()) {
			return form(user, model, form);
		}
		long id;
		try {
			id = service.create(user.accountId(), form);
		}
		catch (IllegalArgumentException ex) {
			model.addAttribute("error", ex.getMessage());
			return form(user, model, form);
		}
		if (form.isSendNow()) {
			List<OutboundMessage> sent = service.send(user.accountId(), id);
			redirect.addFlashAttribute("notice", sentNotice(sent));
		}
		else {
			redirect.addFlashAttribute("notice", "Invoice created as a draft. Check the PDF, then send it.");
		}
		return "redirect:/invoices/" + id;
	}

	@GetMapping("/{id}")
	public String detail(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		Invoice invoice = invoices.get(user.accountId(), id);
		Party party = parties.get(user.accountId(), invoice.partyId());
		List<Trip> billed = trips.ofInvoice(user.accountId(), id);
		model.addAttribute("invoice", invoice);
		model.addAttribute("party", party);
		model.addAttribute("lines", invoices.lines(id));
		model.addAttribute("trips", billed);
		model.addAttribute("pods", billed.stream().flatMap((t) -> pods.ofTrip(user.accountId(), t.id()).stream()).toList());
		model.addAttribute("payments", invoices.payments(id));
		model.addAttribute("followUps", followUps.forInvoice(user.accountId(), id));
		model.addAttribute("messages", outbox.about(user.accountId(), "invoice", id));
		model.addAttribute("today", LocalDate.now(clock));
		model.addAttribute("gstMode", GstMode.valueOf(invoice.gstMode()));
		PaymentForm payment = new PaymentForm();
		payment.setReceivedOn(LocalDate.now(clock));
		payment.setAmount(invoice.balance().max(BigDecimal.ZERO));
		model.addAttribute("payment", payment);
		return "freight/invoice";
	}

	@GetMapping("/{id}/pdf")
	public String pdf(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
		Invoice invoice = invoices.get(user.accountId(), id);
		long fileId = invoice.pdfFileId() != null ? invoice.pdfFileId() : service.renderPdf(user.accountId(), id).id();
		return "redirect:/files/" + fileId;
	}

	@PostMapping("/{id}/send")
	public String send(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, RedirectAttributes redirect) {
		Invoice invoice = invoices.get(user.accountId(), id);
		List<OutboundMessage> sent = invoice.isDraft() ? service.send(user.accountId(), id)
				: chase.remindNow(user.accountId(), id);
		redirect.addFlashAttribute("notice", sentNotice(sent));
		return "redirect:/invoices/" + id;
	}

	@PostMapping("/{id}/payments")
	public String pay(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@Valid @ModelAttribute("payment") PaymentForm form, BindingResult result, RedirectAttributes redirect) {
		if (result.hasErrors() || form.getAmount() == null || form.getAmount().signum() <= 0
				&& (form.getTds() == null || form.getTds().signum() <= 0)) {
			redirect.addFlashAttribute("error", "Enter the amount received and the date.");
			return "redirect:/invoices/" + id;
		}
		try {
			service.recordPayment(user.accountId(), id, form);
			redirect.addFlashAttribute("notice", "Payment recorded.");
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/invoices/" + id;
	}

	@PostMapping("/{id}/promise")
	public String promise(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate promisedOn,
			@RequestParam(required = false) String note, RedirectAttributes redirect) {
		service.recordPromise(user.accountId(), id, promisedOn, note);
		redirect.addFlashAttribute("notice", "Promise noted. Reminders resume the day after if unpaid.");
		return "redirect:/invoices/" + id;
	}

	@PostMapping("/{id}/note")
	public String note(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam String note) {
		if (!note.isBlank()) {
			service.addNote(user.accountId(), id, note);
		}
		return "redirect:/invoices/" + id;
	}

	@PostMapping("/{id}/pause")
	public String pause(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam boolean paused) {
		service.setPaused(user.accountId(), id, paused);
		return "redirect:/invoices/" + id;
	}

	@PostMapping("/{id}/status")
	public String status(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam String status,
			@RequestParam(required = false) String note, RedirectAttributes redirect) {
		try {
			service.setStatus(user.accountId(), id, status, note);
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/invoices/" + id;
	}

	private String form(CurrentUser user, Model model, NewInvoiceForm form) {
		List<Trip> selected = trips.ofIds(user.accountId(), form.getTripIds());
		model.addAttribute("form", form);
		model.addAttribute("selected", selected);
		model.addAttribute("parties", parties.all(user.accountId()));
		model.addAttribute("gstModes", GstMode.values());
		model.addAttribute("subtotal", selected.stream().map(Trip::billable).reduce(BigDecimal.ZERO, BigDecimal::add));
		model.addAttribute("advance",
				selected.stream().map(Trip::advanceAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
		return "freight/invoice-new";
	}

	private static String sentNotice(List<OutboundMessage> sent) {
		if (sent.isEmpty()) {
			return "Nothing was sent: add a WhatsApp number or email for this party.";
		}
		long failed = sent.stream().filter(OutboundMessage::failed).count();
		String channels = String.join(" and ", sent.stream().map((m) -> m.isWhatsApp() ? "WhatsApp" : "email").toList());
		return failed > 0 ? "Sent by " + channels + ", but " + failed + " failed. See the outbox."
				: "Sent by " + channels + " with the PODs attached.";
	}

}
