package com.liabletech.freight.invoice;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.liabletech.freight.chase.ChaseMessages;
import com.liabletech.freight.chase.ChasePlan;
import com.liabletech.freight.chase.ChaseSettings;
import com.liabletech.freight.chase.ChaseSettingsRepository;
import com.liabletech.freight.chase.FollowUp;
import com.liabletech.freight.chase.FollowUps;
import com.liabletech.freight.fleet.Fleet;
import com.liabletech.freight.fleet.Vehicle;
import com.liabletech.freight.party.Parties;
import com.liabletech.freight.party.Party;
import com.liabletech.freight.pod.Pod;
import com.liabletech.freight.pod.Pods;
import com.liabletech.freight.trip.Trip;
import com.liabletech.freight.trip.TripForm;
import com.liabletech.freight.trip.Trips;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.messaging.Messenger;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.messaging.Outgoing;
import com.liabletech.platform.numbering.DocumentNumbers;
import com.liabletech.platform.text.IndianMoney;

/** Billing delivered trips, sending the bill with its proof, and recording what comes back. */
@Service
public class InvoiceService {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

	private final Invoices invoices;

	private final Trips trips;

	private final Pods pods;

	private final Parties parties;

	private final Fleet fleet;

	private final Accounts accounts;

	private final DocumentNumbers numbers;

	private final InvoicePdf pdf;

	private final FileStore files;

	private final Messenger messenger;

	private final ChaseSettingsRepository settings;

	private final FollowUps followUps;

	private final Clock clock;

	public InvoiceService(Invoices invoices, Trips trips, Pods pods, Parties parties, Fleet fleet, Accounts accounts,
			DocumentNumbers numbers, InvoicePdf pdf, FileStore files, Messenger messenger,
			ChaseSettingsRepository settings, FollowUps followUps, Clock clock) {
		this.invoices = invoices;
		this.trips = trips;
		this.pods = pods;
		this.parties = parties;
		this.fleet = fleet;
		this.accounts = accounts;
		this.numbers = numbers;
		this.pdf = pdf;
		this.files = files;
		this.messenger = messenger;
		this.settings = settings;
		this.followUps = followUps;
		this.clock = clock;
	}

	/**
	 * One invoice for the chosen delivered trips. Each trip contributes a freight line, plus lines
	 * for detention and other charges; advances already received are deducted.
	 */
	@Transactional
	public long create(long accountId, NewInvoiceForm form) {
		Account account = accounts.get(accountId);
		Party party = parties.get(accountId, form.getPartyId());
		List<Trip> selected = trips.ofIds(accountId, form.getTripIds());
		if (selected.isEmpty() || selected.size() != form.getTripIds().stream().distinct().count()) {
			throw new IllegalArgumentException("Choose delivered trips from your own list.");
		}
		for (Trip trip : selected) {
			if (!trip.billableNow()) {
				throw new IllegalArgumentException("LR " + trip.lrNumber() + " is not ready to bill (it needs a POD and no invoice yet).");
			}
			if (trip.partyId() != null && !trip.partyId().equals(party.id())) {
				throw new IllegalArgumentException("LR " + trip.lrNumber() + " belongs to another party.");
			}
			if (trip.freightAmount() == null) {
				throw new IllegalArgumentException("Enter the freight amount for LR " + trip.lrNumber() + " first.");
			}
		}
		ChaseSettings chase = settings.get(accountId);
		List<Line> lines = new ArrayList<>();
		BigDecimal advance = BigDecimal.ZERO;
		for (Trip trip : selected) {
			String vehicle = fleet.vehicle(accountId, trip.vehicleId()).map(Vehicle::registration).orElse(null);
			StringBuilder description = new StringBuilder("Freight");
			if (StringUtils.hasText(trip.lrNumber())) {
				description.append(" for LR ").append(trip.lrNumber());
			}
			if (trip.lrDate() != null) {
				description.append(" dt ").append(DATE.format(trip.lrDate()));
			}
			if (!trip.route().isEmpty()) {
				description.append(", ").append(trip.route());
			}
			if (vehicle != null) {
				description.append(", vehicle ").append(vehicle);
			}
			if (StringUtils.hasText(trip.goods())) {
				description.append(" (").append(trip.goods()).append(")");
			}
			lines.add(new Line(trip.id(), description.toString(), trip.freightAmount()));
			if (trip.detentionAmount().signum() > 0) {
				lines.add(new Line(trip.id(), "Detention charges, LR " + trip.lrNumber(), trip.detentionAmount()));
			}
			if (trip.otherCharges().signum() > 0) {
				lines.add(new Line(trip.id(), "Other charges (loading/unloading), LR " + trip.lrNumber(), trip.otherCharges()));
			}
			advance = advance.add(trip.advanceAmount());
		}
		BigDecimal subtotal = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
		GstMode.Totals totals = form.getGstMode().compute(subtotal, form.getGstRate(), account.stateCode(),
				party.stateCode(), advance);
		String number = numbers.next(accountId, chase.invoicePrefix(), form.getInvoiceDate());
		long invoiceId = invoices.create(accountId, number, form.getInvoiceDate(),
				form.getInvoiceDate().plusDays(party.creditDays()), party.id(), form.getGstMode(), form.getGstRate(), totals,
				StringUtils.hasText(form.getNotes()) ? form.getNotes().trim() : null);
		for (int i = 0; i < lines.size(); i++) {
			invoices.addLine(invoiceId, lines.get(i).tripId(), lines.get(i).description(), lines.get(i).amount(), i + 1);
		}
		trips.attachToInvoice(accountId, selected.stream().map(Trip::id).toList(), invoiceId);
		for (Trip trip : selected) {
			if (trip.partyId() == null) {
				trips.update(accountId, trip.id(), withParty(trip, party.id()));
			}
		}
		renderPdf(accountId, invoiceId);
		return invoiceId;
	}

	/** Builds the PDF afresh (with the PODs on file now) and stores it on the invoice. */
	public StoredFile renderPdf(long accountId, long invoiceId) {
		Account account = accounts.get(accountId);
		Invoice invoice = invoices.get(accountId, invoiceId);
		Party party = parties.get(accountId, invoice.partyId());
		List<InvoicePdf.Proof> proofs = new ArrayList<>();
		for (Trip trip : trips.ofInvoice(accountId, invoiceId)) {
			for (Pod pod : pods.ofTrip(accountId, trip.id())) {
				String caption = "Proof of delivery · LR " + (trip.lrNumber() == null ? "–" : trip.lrNumber())
						+ (pod.deliveryDate() != null ? " · delivered " + DATE.format(pod.deliveryDate()) : "");
				proofs.add(new InvoicePdf.Proof(caption, files.get(pod.fileId())));
			}
		}
		byte[] bytes = pdf.render(account, party, invoice, invoices.lines(invoiceId), proofs,
				settings.get(accountId).invoiceTerms());
		StoredFile file = files.save(accountId, bytes, "application/pdf", fileName(invoice));
		invoices.setPdf(invoiceId, file.id());
		return file;
	}

	/**
	 * Sends the invoice (with its PODs) to the party by WhatsApp and email, as the chase settings
	 * say, and schedules the first reminder.
	 */
	public List<OutboundMessage> send(long accountId, long invoiceId) {
		Account account = accounts.get(accountId);
		Invoice invoice = invoices.get(accountId, invoiceId);
		Party party = parties.get(accountId, invoice.partyId());
		StoredFile file = renderPdf(accountId, invoiceId);
		List<Trip> billed = trips.ofInvoice(accountId, invoiceId);
		ChaseSettings chase = settings.get(accountId);
		ChaseMessages.Text text = ChaseMessages.invoice(account, party, invoice, billed);
		List<OutboundMessage> sent = deliver(account, party, invoice, file, text, chase,
				"freight_invoice", party.name(), invoice.number(), IndianMoney.rupees(invoice.balance()),
				DATE.format(invoice.dueDate()), account.name());
		OffsetDateTime now = OffsetDateTime.now(clock);
		LocalDate today = LocalDate.now(clock);
		LocalDate next = ChasePlan.next(invoice.dueDate(), chase.offsets(), chase.repeatEveryDays(), today,
				invoice.promisedOn());
		invoices.markSent(invoiceId, now, next);
		followUps.record(accountId, invoiceId, now, FollowUp.SENT, 0, summary(sent),
				sent.isEmpty() ? null : sent.getFirst().id());
		return sent;
	}

	/** Sends the reminder text through the channels the owner chose, attaching the invoice PDF. */
	public List<OutboundMessage> deliver(Account account, Party party, Invoice invoice, StoredFile pdfFile,
			ChaseMessages.Text text, ChaseSettings chase, String template, String... templateParameters) {
		List<OutboundMessage> sent = new ArrayList<>();
		if (chase.sendWhatsapp() && StringUtils.hasText(party.phone())) {
			sent.add(messenger.send(Outgoing.whatsApp(account.id(), party.phone(), text.body())
				.attach(pdfFile.id(), pdfFile.downloadName())
				.template(template, templateParameters)
				.about("invoice", invoice.id())));
		}
		if (chase.sendEmail() && StringUtils.hasText(party.email())) {
			sent.add(messenger.send(Outgoing.email(account.id(), party.email(), text.subject(), text.body())
				.attach(pdfFile.id(), pdfFile.downloadName())
				.about("invoice", invoice.id())));
		}
		return sent;
	}

	@Transactional
	public void recordPayment(long accountId, long invoiceId, PaymentForm form) {
		Invoice invoice = invoices.get(accountId, invoiceId);
		if (invoice.isDraft()) {
			throw new IllegalArgumentException("Send the invoice before recording a payment.");
		}
		invoices.insertPayment(accountId, invoiceId, form);
		BigDecimal tds = form.getTds() == null ? BigDecimal.ZERO : form.getTds();
		BigDecimal received = invoice.received().add(form.getAmount());
		BigDecimal totalTds = invoice.tds().add(tds);
		boolean settled = invoice.total().subtract(received).subtract(totalTds).compareTo(BigDecimal.ONE) < 0;
		invoices.recordPaymentTotals(invoiceId, received, totalTds, settled ? Invoice.PAID : Invoice.PART_PAID,
				settled ? form.getReceivedOn() : null);
		if (settled) {
			trips.markInvoicePaid(accountId, invoiceId);
		}
		String note = IndianMoney.rupees(form.getAmount()) + " by " + form.getMode()
				+ (tds.signum() > 0 ? " + TDS " + IndianMoney.rupees(tds) : "")
				+ (StringUtils.hasText(form.getReference()) ? " (ref " + form.getReference().trim() + ")" : "");
		followUps.record(accountId, invoiceId, OffsetDateTime.now(clock), FollowUp.PAYMENT, null, note, null);
		if (form.isThankYou()) {
			Party party = parties.get(accountId, invoice.partyId());
			if (StringUtils.hasText(party.phone())) {
				messenger.send(Outgoing.whatsApp(accountId, party.phone(),
						ChaseMessages.thankYou(accounts.get(accountId), party, invoice, form.getAmount()))
					.about("invoice", invoiceId));
			}
		}
	}

	/** The party promised to pay by a date: chasing pauses until the day after. */
	@Transactional
	public void recordPromise(long accountId, long invoiceId, LocalDate promisedOn, String note) {
		Invoice invoice = invoices.get(accountId, invoiceId);
		invoices.setPromise(accountId, invoiceId, promisedOn, promisedOn.plusDays(1));
		followUps.record(accountId, invoice.id(), OffsetDateTime.now(clock), FollowUp.PROMISE, null,
				"Promised to pay by " + DATE.format(promisedOn) + (StringUtils.hasText(note) ? ": " + note.trim() : ""),
				null);
	}

	public void addNote(long accountId, long invoiceId, String note) {
		invoices.get(accountId, invoiceId);
		followUps.record(accountId, invoiceId, OffsetDateTime.now(clock), FollowUp.NOTE, null, note.trim(), null);
	}

	@Transactional
	public void setPaused(long accountId, long invoiceId, boolean paused) {
		invoices.get(accountId, invoiceId);
		invoices.setChasePaused(accountId, invoiceId, paused);
		followUps.record(accountId, invoiceId, OffsetDateTime.now(clock), FollowUp.STATUS, null,
				paused ? "Automatic reminders paused" : "Automatic reminders resumed", null);
	}

	/** Disputed invoices are not chased automatically; written-off ones are closed. */
	@Transactional
	public void setStatus(long accountId, long invoiceId, String status, String note) {
		Invoice invoice = invoices.get(accountId, invoiceId);
		if (!invoice.isOpen() || !(Invoice.DISPUTED.equals(status) || Invoice.WRITTEN_OFF.equals(status)
				|| Invoice.SENT.equals(status))) {
			throw new IllegalArgumentException("That change is not possible for this invoice.");
		}
		String target = Invoice.SENT.equals(status) && invoice.received().signum() > 0 ? Invoice.PART_PAID : status;
		invoices.setStatus(accountId, invoiceId, target);
		followUps.record(accountId, invoiceId, OffsetDateTime.now(clock), FollowUp.STATUS, null,
				switch (target) {
					case Invoice.DISPUTED -> "Marked disputed";
					case Invoice.WRITTEN_OFF -> "Written off";
					default -> "Dispute resolved; chasing again";
				} + (StringUtils.hasText(note) ? ": " + note.trim() : ""), null);
	}

	private static String fileName(Invoice invoice) {
		return "Invoice-" + invoice.number().replaceAll("[^A-Za-z0-9-]", "-") + ".pdf";
	}

	private static String summary(List<OutboundMessage> sent) {
		if (sent.isEmpty()) {
			return "Not sent: the party has no WhatsApp number or email";
		}
		return "Sent by " + String.join(" and ", sent.stream()
			.map((m) -> (m.isWhatsApp() ? "WhatsApp" : "email") + (m.failed() ? " (failed)" : ""))
			.toList());
	}

	private static TripForm withParty(Trip trip, long partyId) {
		TripForm form = TripForm.of(trip);
		form.setPartyId(partyId);
		return form;
	}

	private record Line(Long tripId, String description, BigDecimal amount) {
	}

}
