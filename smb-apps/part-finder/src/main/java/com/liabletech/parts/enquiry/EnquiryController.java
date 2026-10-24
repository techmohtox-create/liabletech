package com.liabletech.parts.enquiry;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.parts.booking.Bookings;
import com.liabletech.parts.catalog.Part;
import com.liabletech.parts.catalog.PartType;
import com.liabletech.parts.catalog.Parts;
import com.liabletech.parts.catalog.VehicleCatalog;
import com.liabletech.parts.mechanic.Mechanic;
import com.liabletech.parts.mechanic.Mechanics;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.messaging.Outbox;
import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.text.Lenient;
import com.liabletech.platform.whatsapp.InboundMessages;

/** One enquiry: what came in, what it was taken to be, the parts that fit and the answer. */
@Controller
@RequestMapping("/enquiries/{id}")
public class EnquiryController {

	static final List<String> POSITIONS = List.of("FRONT", "REAR", "LEFT", "RIGHT", "FRONT LEFT", "FRONT RIGHT",
			"REAR LEFT", "REAR RIGHT");

	static final List<String> QUESTIONS = List.of("Please send a photo of the number on the part.",
			"Which vehicle and model year is it for?", "Front or rear?", "Petrol or diesel?",
			"Please send a photo of the old part.");

	private final Enquiries enquiries;

	private final EnquiryService service;

	private final QuoteOptions options;

	private final Parts parts;

	private final Mechanics mechanics;

	private final Bookings bookings;

	private final FileStore files;

	private final InboundMessages inbound;

	private final Outbox outbox;

	private final Clock clock;

	public EnquiryController(Enquiries enquiries, EnquiryService service, QuoteOptions options, Parts parts,
			Mechanics mechanics, Bookings bookings, FileStore files, InboundMessages inbound, Outbox outbox, Clock clock) {
		this.enquiries = enquiries;
		this.service = service;
		this.options = options;
		this.parts = parts;
		this.mechanics = mechanics;
		this.bookings = bookings;
		this.files = files;
		this.inbound = inbound;
		this.outbox = outbox;
		this.clock = clock;
	}

	@GetMapping
	public String enquiry(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(required = false) String q, Model model) {
		long account = user.accountId();
		Enquiry enquiry = enquiries.get(account, id);
		Mechanic mechanic = mechanics.get(account, enquiry.mechanicId());
		List<PartMatcher.Candidate> candidates = service.candidates(enquiry);
		List<Part> searched = parts.search(account, q, 15)
			.stream()
			.filter((p) -> candidates.stream().noneMatch((c) -> c.part().id().equals(p.id())))
			.toList();
		model.addAttribute("enquiry", enquiry);
		model.addAttribute("found", enquiry.identification());
		model.addAttribute("mechanic", mechanic);
		model.addAttribute("file", enquiry.fileId() == null ? null : files.find(enquiry.fileId()).orElse(null));
		model.addAttribute("candidates", candidates.stream().limit(8).toList());
		model.addAttribute("searched", searched);
		model.addAttribute("q", q);
		model.addAttribute("offered", options.forEnquiry(id));
		model.addAttribute("booking", bookings.forEnquiry(id).orElse(null));
		model.addAttribute("thread", thread(account, mechanic));
		model.addAttribute("types", Arrays.stream(PartType.values()).toList());
		model.addAttribute("positions", POSITIONS);
		model.addAttribute("questions", QUESTIONS);
		model.addAttribute("models", VehicleCatalog.all());
		model.addAttribute("now", java.time.OffsetDateTime.now(clock));
		return "parts/enquiry";
	}

	/** Quote the ticked parts (at their price, or the price typed beside them) and perhaps one typed-in item. */
	@PostMapping("/quote")
	public String quote(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(required = false) List<Long> partId, @RequestParam Map<String, String> form,
			@RequestParam(defaultValue = "/inbox") String back, RedirectAttributes redirect) {
		long account = user.accountId();
		List<QuoteOptions.NewOption> chosen = new ArrayList<>();
		for (Long part : partId == null ? List.<Long>of() : partId) {
			parts.find(account, part).ifPresent((p) -> chosen.add(new QuoteOptions.NewOption(p.id(), p.describe(),
					EnquiryService.priceOr(Lenient.number(form.get("price_" + p.id())), p))));
		}
		String description = Lenient.text(form.get("customDescription"));
		BigDecimal price = Lenient.number(form.get("customPrice"));
		if (description != null && price != null && price.signum() > 0) {
			chosen.add(new QuoteOptions.NewOption(null, description, price));
		}
		try {
			service.quote(account, id, chosen);
			redirect.addFlashAttribute("notice", "Quote sent on WhatsApp.");
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:" + safe(back, id);
	}

	@PostMapping("/unavailable")
	public String unavailable(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(required = false) String note, @RequestParam(defaultValue = "/inbox") String back,
			RedirectAttributes redirect) {
		service.notAvailable(user.accountId(), id, note);
		redirect.addFlashAttribute("notice", "Told the mechanic it is not in stock. It counts in missed demand.");
		return "redirect:" + safe(back, id);
	}

	@PostMapping("/message")
	public String message(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(required = false) String text, @RequestParam(required = false) String question,
			@RequestParam(defaultValue = "/inbox") String back, RedirectAttributes redirect) {
		try {
			service.message(user.accountId(), id, StringUtils.hasText(text) ? text : question);
			redirect.addFlashAttribute("notice", "Sent on WhatsApp.");
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:" + safe(back, id);
	}

	@PostMapping("/close")
	public String close(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(defaultValue = "/inbox") String back) {
		service.close(user.accountId(), id);
		return "redirect:" + safe(back, id);
	}

	/** The counter's correction of what was asked for; the matching parts follow it. */
	@PostMapping("/identify")
	public String identify(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(required = false) String partType, @RequestParam(required = false) String partName,
			@RequestParam(required = false) String partNumbers, @RequestParam(required = false) String vehicleModel,
			@RequestParam(required = false) String vehicleYear, @RequestParam(required = false) String position,
			@RequestParam(defaultValue = "1") int quantity) {
		Enquiry enquiry = enquiries.get(user.accountId(), id);
		String model = Lenient.text(vehicleModel);
		List<String> numbers = partNumbers == null ? List.of()
				: Arrays.stream(partNumbers.split("[,;\\s]+")).map(String::trim).filter((s) -> !s.isEmpty())
					.map(String::toUpperCase).toList();
		VehicleCatalog.Vehicle known = model == null ? null : VehicleCatalog.find(model).orElse(null);
		Identification edited = new Identification(PartType.parse(partType), Lenient.text(partName), numbers,
				known != null ? known.make() : null, model == null ? null : VehicleCatalog.canonical(model),
				TextParser.year(vehicleYear == null ? "" : vehicleYear), POSITIONS.contains(position) ? position : null,
				Math.max(1, Math.min(99, quantity)), enquiry.identification().confidence(), null, Identification.EDITED, null);
		service.correct(user.accountId(), id, edited);
		return "redirect:/enquiries/" + id;
	}

	/** The WhatsApp conversation with the mechanic, both ways, oldest first. */
	private List<Bubble> thread(long account, Mechanic mechanic) {
		List<Bubble> bubbles = new ArrayList<>();
		inbound.recentFromPhone(account, mechanic.phone(), 20)
			.forEach((m) -> bubbles.add(new Bubble(false, m.textBody(), m.fileId(), m.messageType(), m.receivedAt())));
		outbox.whatsAppToPhone(account, mechanic.phone(), 20)
			.forEach((m) -> bubbles.add(new Bubble(true, m.body(), null, "text", m.createdAt())));
		bubbles.sort(Comparator.comparing(Bubble::at));
		return bubbles.size() > 30 ? bubbles.subList(bubbles.size() - 30, bubbles.size()) : bubbles;
	}

	/** Only paths inside the app; anything else goes back to the enquiry. */
	private static String safe(String back, long id) {
		return back != null && back.startsWith("/") && !back.startsWith("//") ? back : "/enquiries/" + id;
	}

	public record Bubble(boolean outgoing, String text, Long fileId, String type, java.time.OffsetDateTime at) {
	}

}
