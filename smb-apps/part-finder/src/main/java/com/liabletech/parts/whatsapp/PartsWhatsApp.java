package com.liabletech.parts.whatsapp;

import java.awt.Color;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.liabletech.parts.enquiry.Enquiries;
import com.liabletech.parts.enquiry.Enquiry;
import com.liabletech.parts.enquiry.EnquiryService;
import com.liabletech.parts.enquiry.PartMessages;
import com.liabletech.parts.enquiry.QuoteOptions;
import com.liabletech.parts.mechanic.Mechanic;
import com.liabletech.parts.mechanic.Mechanics;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.whatsapp.InboundContext;
import com.liabletech.platform.whatsapp.InboundHandler;
import com.liabletech.platform.whatsapp.InboundMessage;
import com.liabletech.platform.whatsapp.InboundRouter;
import com.liabletech.platform.whatsapp.SimulatorDirectory;

/**
 * Mechanics write to the shop's number. A photo, voice note or message is a new enquiry; "1" or
 * "2" books an option from the last quote; CANCEL releases the last booking; a few more words soon
 * after an enquiry ("front", "2019") are added to it.
 */
@Component
public class PartsWhatsApp implements InboundRouter, InboundHandler, SimulatorDirectory {

	private static final Set<String> YES = Set.of("yes", "ok", "okay", "haan", "ha", "han", "book", "book it", "confirm",
			"done", "👍", "theek hai", "thik hai", "bhej do", "rakh do");

	private static final Set<String> CANCEL = Set.of("cancel", "cancel booking", "cancel karo", "nahi chahiye", "mat rakho");

	private static final Set<String> GREETINGS = Set.of("hi", "hii", "hello", "hey", "namaste", "namaskar", "help", "menu",
			"?", "ram ram", "salaam");

	private static final Pattern OPTION = Pattern
		.compile("^(?:option|opt|no\\.?|number|#)?\\s*([1-9])(?:\\s*(?:wala|vala|wali|chahiye|book|please|pls|kar do|kardo))*$");

	private final Mechanics mechanics;

	private final Enquiries enquiries;

	private final QuoteOptions options;

	private final EnquiryService service;

	private final Clock clock;

	public PartsWhatsApp(Mechanics mechanics, Enquiries enquiries, QuoteOptions options, EnquiryService service,
			Clock clock) {
		this.mechanics = mechanics;
		this.enquiries = enquiries;
		this.options = options;
		this.service = service;
		this.clock = clock;
	}

	/** On the shared number: the shop this mechanic asked last. Shops on their own number get everyone. */
	@Override
	public Optional<Long> accountFor(String fromPhone) {
		return mechanics.accountsFor(fromPhone).stream().findFirst();
	}

	@Override
	public void handle(InboundContext context) {
		InboundMessage message = context.message();
		long accountId = context.accountId();
		OffsetDateTime now = OffsetDateTime.now(clock);
		Mechanic mechanic = mechanics.findOrCreate(accountId, context.from(), message.contactName(), now);
		if (mechanic.blocked()) {
			return;
		}
		String text = message.text() == null ? "" : message.text().trim();
		String lower = text.toLowerCase(Locale.ROOT).replaceAll("[!.]+$", "").trim();
		if (!message.hasMedia()) {
			if (CANCEL.contains(lower)) {
				context.reply(service.cancelByMechanic(accountId, mechanic).orElse("Nothing is booked to cancel."));
				return;
			}
			Optional<Enquiry> quoted = enquiries.quotedFor(mechanic.id(), now.minusDays(1));
			if (quoted.isPresent()) {
				Integer option = option(lower, options.forEnquiry(quoted.get().id()).size());
				if (option != null) {
					context.reply(service.book(accountId, mechanic, quoted.get(), option), "enquiry", quoted.get().id());
					return;
				}
			}
			if (lower.isEmpty() || GREETINGS.contains(lower)) {
				context.reply("Namaste! " + PartMessages.HELP);
				return;
			}
			if (quoted.isEmpty() && OPTION.matcher(lower).matches()) {
				context.reply("There is no open quote to book; quotes stay open for a day. " + PartMessages.HELP);
				return;
			}
			Optional<Enquiry> waiting = enquiries.waitingFrom(mechanic.id(), now.minusMinutes(30));
			if (waiting.isPresent()) {
				service.addDetail(accountId, waiting.get(), text);
				return;
			}
		}
		Optional<StoredFile> file = context.file();
		if (message.hasMedia() && file.isEmpty()) {
			context.reply("We could not open that. Please send it again.");
			return;
		}
		String kind = message.isAudio() ? Enquiry.VOICE : message.hasMedia() ? Enquiry.PHOTO : Enquiry.TEXT;
		service.receive(accountId, mechanic, message.id(), kind, file.orElse(null), text.isEmpty() ? null : text);
	}

	/** "1", "option 2", "2 wala", or YES when only one option was offered. */
	static Integer option(String text, int offered) {
		if (YES.contains(text)) {
			return offered == 1 ? 1 : null;
		}
		Matcher matcher = OPTION.matcher(text);
		return matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
	}

	@Override
	public String replyToUnknownSender(InboundMessage message) {
		return "This number is not set up with a spare parts shop here. Please message your parts shop directly.";
	}

	@Override
	public List<Contact> contacts(long accountId) {
		List<Contact> contacts = new ArrayList<>();
		for (Mechanics.MechanicRow row : mechanics.list(accountId, null, OffsetDateTime.now(clock).minusDays(30), 8)) {
			if (!row.blocked()) {
				contacts.add(new Contact(row.name() != null ? row.name() : row.phone(), row.phone(),
						"Mechanic" + (row.garage() != null ? " · " + row.garage() : "")));
			}
		}
		return contacts;
	}

	@Override
	public List<Sample> samples(long accountId) {
		return List.of(
				new Sample("brake-box", "Brake pad box", "Swift ka front brake pad chahiye",
						() -> new SampleFile(PartSamples.box("ProStop", "Disc brake pad set", "Front axle", "BP-SWF-PS",
								"Maruti Swift / Dzire 2018–2024", new Color(176, 30, 40)), "image/jpeg", "brake-box.jpg")),
				new Sample("clutch-tag", "Old clutch plate tag", "Bolero clutch plate, urgent",
						() -> new SampleFile(PartSamples.tag("Bolero 2016", "Clutch plate", "Gurpreet - Punjab Diesel"),
								"image/jpeg", "clutch-tag.jpg")),
				new Sample("oil-filter", "Oil filter box", "Alto 800 oil filter x4",
						() -> new SampleFile(PartSamples.box("Filtra", "Oil filter", "Spin-on, petrol", "OF-MS-110",
								"Maruti Alto / WagonR / Swift / Dzire", new Color(21, 94, 117)), "image/jpeg", "filter.jpg")));
	}

}
