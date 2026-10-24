package com.liabletech.parts.enquiry;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.liabletech.parts.PartsNavigation;
import com.liabletech.parts.booking.Booking;
import com.liabletech.parts.booking.Bookings;
import com.liabletech.parts.catalog.Part;
import com.liabletech.parts.catalog.Parts;
import com.liabletech.parts.mechanic.Mechanic;
import com.liabletech.parts.mechanic.Mechanics;
import com.liabletech.parts.settings.ShopSettings;
import com.liabletech.parts.settings.ShopSettingsRepository;
import com.liabletech.platform.AppProperties;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.messaging.Messenger;
import com.liabletech.platform.messaging.Outgoing;
import com.liabletech.platform.text.Phones;

/**
 * An enquiry from arrival to answer. It is understood and matched to stock at once; a sure match is
 * quoted straight away on Pro, anything else is acknowledged and waits at the counter, where one tap
 * sends the quote. The mechanic books by replying with the option's number.
 */
@Service
public class EnquiryService {

	private static final Logger log = LoggerFactory.getLogger(EnquiryService.class);

	/** A second message this soon after an unanswered enquiry adds to it instead of starting another. */
	static final Duration DETAIL_WINDOW = Duration.ofMinutes(30);

	/** How long a quote can still be booked by replying with its number. */
	static final Duration QUOTE_VALID = Duration.ofHours(24);

	static final int MAX_OPTIONS = 3;

	private final Enquiries enquiries;

	private final QuoteOptions options;

	private final Parts parts;

	private final Mechanics mechanics;

	private final Bookings bookings;

	private final Identifier identifier;

	private final ShopSettingsRepository settings;

	private final Messenger messenger;

	private final Accounts accounts;

	private final AppProperties properties;

	private final Clock clock;

	public EnquiryService(Enquiries enquiries, QuoteOptions options, Parts parts, Mechanics mechanics, Bookings bookings,
			Identifier identifier, ShopSettingsRepository settings, Messenger messenger, Accounts accounts,
			AppProperties properties, Clock clock) {
		this.enquiries = enquiries;
		this.options = options;
		this.parts = parts;
		this.mechanics = mechanics;
		this.bookings = bookings;
		this.identifier = identifier;
		this.settings = settings;
		this.messenger = messenger;
		this.accounts = accounts;
		this.properties = properties;
		this.clock = clock;
	}

	/** A new enquiry: understood, matched, and quoted or acknowledged. Returns the enquiry. */
	public Enquiry receive(long accountId, Mechanic mechanic, Long inboundId, String kind, StoredFile file, String text) {
		Identifier.Result result = identifier.identify(accountId, kind, file, text);
		return open(accountId, mechanic, inboundId, kind, file, text, result.transcript(), result.identification(),
				OffsetDateTime.now(clock));
	}

	/** Records an enquiry already understood, then quotes or acknowledges it like any other. */
	public Enquiry open(long accountId, Mechanic mechanic, Long inboundId, String kind, StoredFile file, String text,
			String transcript, Identification found, OffsetDateTime at) {
		long id = enquiries.create(accountId, mechanic.id(), inboundId, kind, file == null ? null : file.id(), text,
				transcript, found, at);
		answerOrAcknowledge(enquiries.get(accountId, id), true);
		return enquiries.get(accountId, id);
	}

	/**
	 * More words for an enquiry still waiting ("front", "2019 model"): understood together with
	 * what came before, and answered if that makes it sure.
	 */
	public Enquiry addDetail(long accountId, Enquiry enquiry, String text) {
		String combined = StringUtils.hasText(enquiry.text()) ? enquiry.text() + " / " + text : text;
		Identification before = enquiry.identification();
		Identification words = TextParser.parse(combined);
		Identification known = Identification.MANUAL.equals(before.status()) ? words : Identifier.merge(before, words);
		Identification updated = known
			.withQuestion(TextParser.question(known.partType(), known.knowsVehicle(), known.position(), known.partNumbers()))
			.withConfidence(Math.max(before.confidence(), words.confidence()));
		enquiries.identify(accountId, enquiry.id(), updated);
		enquiries.addText(accountId, enquiry.id(), combined);
		answerOrAcknowledge(enquiries.get(accountId, enquiry.id()), false);
		return enquiries.get(accountId, enquiry.id());
	}

	private void answerOrAcknowledge(Enquiry enquiry, boolean first) {
		Account account = accounts.get(enquiry.accountId());
		ShopSettings config = settings.get(enquiry.accountId());
		Mechanic mechanic = mechanics.get(enquiry.accountId(), enquiry.mechanicId());
		Identification found = enquiry.identification();
		List<PartMatcher.Candidate> sure = candidates(enquiry).stream()
			.filter((c) -> c.sure() && c.part().stock() >= enquiry.quantity())
			.limit(MAX_OPTIONS)
			.toList();
		boolean automatic = PartsNavigation.isPro(account) && config.autoQuote() && !sure.isEmpty()
				&& found.question() == null && found.confidence() >= config.autoQuoteConfidence().doubleValue();
		if (automatic) {
			sendQuote(account, enquiry, sure.stream().map((c) -> option(c.part())).toList(), Enquiry.AUTO);
			return;
		}
		if (first && config.acknowledge()) {
			send(account, mechanic, enquiry.id(), PartMessages.acknowledge(account, found));
		}
	}

	/** Parts that could answer the enquiry, best first. */
	public List<PartMatcher.Candidate> candidates(Enquiry enquiry) {
		Identification found = enquiry.identification();
		List<Part> possible = parts.candidates(enquiry.accountId(), found.partType(), found.partNumbers());
		return PartMatcher.match(found, possible, parts.fitments(possible.stream().map(Part::id).toList()));
	}

	/** The counter's quote: chosen parts at their price (or a price typed in), and perhaps one item typed in. */
	public void quote(long accountId, long enquiryId, List<QuoteOptions.NewOption> chosen) {
		if (chosen.isEmpty()) {
			throw new IllegalArgumentException("Pick at least one part to quote.");
		}
		Enquiry enquiry = enquiries.get(accountId, enquiryId);
		sendQuote(accounts.get(accountId), enquiry, chosen.stream().limit(MAX_OPTIONS).toList(), Enquiry.STAFF);
	}

	private void sendQuote(Account account, Enquiry enquiry, List<QuoteOptions.NewOption> chosen, String by) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		Mechanic mechanic = mechanics.get(account.id(), enquiry.mechanicId());
		options.replace(enquiry.id(), chosen, now);
		ShopSettings config = settings.get(account.id());
		send(account, mechanic, enquiry.id(),
				PartMessages.quote(account, enquiry.identification(), chosen, enquiry.quantity(), config.readyMinutes()));
		enquiries.answered(account.id(), enquiry.id(), by, now);
		enquiries.setStatus(account.id(), enquiry.id(), Enquiry.QUOTED, now);
	}

	public QuoteOptions.NewOption option(Part part) {
		return new QuoteOptions.NewOption(part.id(), part.describe(), part.price());
	}

	public void notAvailable(long accountId, long enquiryId, String note) {
		Enquiry enquiry = enquiries.get(accountId, enquiryId);
		Account account = accounts.get(accountId);
		OffsetDateTime now = OffsetDateTime.now(clock);
		send(account, mechanics.get(accountId, enquiry.mechanicId()), enquiryId,
				PartMessages.notAvailable(account, enquiry.identification(), note));
		enquiries.answered(accountId, enquiryId, Enquiry.STAFF, now);
		enquiries.setStatus(accountId, enquiryId, Enquiry.NOT_AVAILABLE, now);
	}

	/** A question or any other message from the counter; the enquiry keeps waiting for the answer. */
	public void message(long accountId, long enquiryId, String text) {
		if (!StringUtils.hasText(text)) {
			throw new IllegalArgumentException("Type the message to send.");
		}
		Enquiry enquiry = enquiries.get(accountId, enquiryId);
		Account account = accounts.get(accountId);
		send(account, mechanics.get(accountId, enquiry.mechanicId()), enquiryId, text.trim() + "\n– " + account.name());
		enquiries.answered(accountId, enquiryId, Enquiry.STAFF, OffsetDateTime.now(clock));
	}

	public void close(long accountId, long enquiryId) {
		enquiries.get(accountId, enquiryId);
		enquiries.setStatus(accountId, enquiryId, Enquiry.CLOSED, OffsetDateTime.now(clock));
	}

	/** The counter's correction of what was asked for; the matches follow it. */
	public void correct(long accountId, long enquiryId, Identification edited) {
		enquiries.get(accountId, enquiryId);
		enquiries.identify(accountId, enquiryId, edited);
	}

	/**
	 * The mechanic replied with an option's number: the part is set aside and the booking confirmed.
	 * If it has sold out meanwhile, the enquiry goes back to the counter.
	 */
	@Transactional
	public String book(long accountId, Mechanic mechanic, Enquiry enquiry, int optionNo) {
		Account account = accounts.get(accountId);
		List<QuoteOptions.QuoteOption> offered = options.forEnquiry(enquiry.id());
		Optional<QuoteOptions.QuoteOption> chosen = offered.stream().filter((o) -> o.optionNo() == optionNo).findFirst();
		if (chosen.isEmpty()) {
			return PartMessages.whichOption(offered.size());
		}
		OffsetDateTime now = OffsetDateTime.now(clock);
		QuoteOptions.QuoteOption option = chosen.get();
		if (option.partId() != null && !parts.changeStock(accountId, option.partId(), -enquiry.quantity(), now)) {
			enquiries.setStatus(accountId, enquiry.id(), Enquiry.NEW, now);
			return PartMessages.soldOut(account);
		}
		long bookingId = bookings.create(accountId, enquiry.id(), mechanic.id(), option.partId(), option.description(),
				enquiry.quantity(), option.price(), now);
		enquiries.setStatus(accountId, enquiry.id(), Enquiry.BOOKED, now);
		return PartMessages.booked(account, bookings.get(accountId, bookingId), settings.get(accountId).readyMinutes());
	}

	/** The mechanic's CANCEL: their latest booking is released back to the shelf. */
	@Transactional
	public Optional<String> cancelByMechanic(long accountId, Mechanic mechanic) {
		Optional<Booking> open = bookings.openFor(mechanic.id());
		if (open.isEmpty()) {
			return Optional.empty();
		}
		release(accountId, open.get());
		return Optional.of(PartMessages.cancelled(accounts.get(accountId), open.get()));
	}

	@Transactional
	public void cancelBooking(long accountId, long bookingId) {
		Booking booking = bookings.get(accountId, bookingId);
		if (Booking.BOOKED.equals(booking.status())) {
			release(accountId, booking);
		}
	}

	public void handOver(long accountId, long bookingId, String paymentMode) {
		Booking booking = bookings.get(accountId, bookingId);
		if (Booking.BOOKED.equals(booking.status())) {
			bookings.finish(accountId, bookingId, Booking.HANDED_OVER, paymentMode, OffsetDateTime.now(clock));
		}
	}

	private void release(long accountId, Booking booking) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		bookings.finish(accountId, booking.id(), Booking.CANCELLED, null, now);
		if (booking.partId() != null) {
			parts.changeStock(accountId, booking.partId(), booking.quantity(), now);
		}
	}

	/** Pro: tells the owner on WhatsApp about enquiries waiting longer than the shop allows. */
	@Scheduled(fixedDelayString = "${app.nudge.every}", initialDelayString = "${app.nudge.every}")
	public void nudge() {
		OffsetDateTime now = OffsetDateTime.now(clock);
		for (Enquiry enquiry : enquiries.waitingLongerThan(now.minusMinutes(1), now.minusHours(2))) {
			try {
				Account account = accounts.get(enquiry.accountId());
				ShopSettings config = settings.get(account.id());
				long waited = Duration.between(enquiry.receivedAt(), now).toMinutes();
				if (!PartsNavigation.isPro(account) || config.nudgeMinutes() <= 0 || waited < config.nudgeMinutes()) {
					continue;
				}
				String owner = Phones.normalize(account.phone());
				if (owner != null) {
					Mechanic mechanic = mechanics.get(account.id(), enquiry.mechanicId());
					String what = enquiry.identification().describe();
					what = what.isBlank() ? (Enquiry.PHOTO.equals(enquiry.kind()) ? "a part (photo)"
							: Enquiry.VOICE.equals(enquiry.kind()) ? "a part (voice note)" : "a part") : what;
					messenger.send(Outgoing.whatsApp(account.id(), owner, "⏱ " + mechanic.displayName() + " has waited "
							+ waited + " min for " + what + ". Answer before another shop does: " + properties.baseUrl()
							+ "/inbox")
						.about("enquiry", enquiry.id())
						.template("parts_waiting_nudge", mechanic.displayName(), String.valueOf(waited), what));
				}
				enquiries.nudged(enquiry.id(), now);
			}
			catch (RuntimeException ex) {
				log.warn("Could not nudge about enquiry {}", enquiry.id(), ex);
			}
		}
	}

	/** A quote not booked within a day is closed; "1" no longer books it. */
	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
	public void closeStaleQuotes() {
		enquiries.closeQuotedBefore(OffsetDateTime.now(clock).minus(QUOTE_VALID), OffsetDateTime.now(clock));
	}

	/** Every part the counter could quote for an enquiry: the matches, then any found by search. */
	public List<Part> quotable(Enquiry enquiry, List<Part> searched) {
		List<Part> all = new ArrayList<>(candidates(enquiry).stream().map(PartMatcher.Candidate::part).toList());
		for (Part part : searched) {
			if (all.stream().noneMatch((p) -> p.id().equals(part.id()))) {
				all.add(part);
			}
		}
		return all;
	}

	private void send(Account account, Mechanic mechanic, long enquiryId, String text) {
		messenger.send(Outgoing.whatsApp(account.id(), mechanic.phone(), text).about("enquiry", enquiryId));
	}

	/** A price typed at the counter, or the part's own when left empty. */
	public static BigDecimal priceOr(BigDecimal typed, Part part) {
		return typed != null && typed.signum() > 0 ? typed : part.price();
	}

}
