package com.liabletech.exportdocs.shipment;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.liabletech.exportdocs.check.CheckService;
import com.liabletech.exportdocs.check.Finding;
import com.liabletech.exportdocs.check.Findings;
import com.liabletech.exportdocs.check.Fixer;
import com.liabletech.exportdocs.check.LcTerms;
import com.liabletech.exportdocs.document.DocData;
import com.liabletech.exportdocs.document.DocFields;
import com.liabletech.exportdocs.document.DocReader;
import com.liabletech.exportdocs.document.DocType;
import com.liabletech.exportdocs.document.Records;
import com.liabletech.exportdocs.document.ShipmentDoc;
import com.liabletech.exportdocs.document.ShipmentDocs;
import com.liabletech.exportdocs.reading.LcReading;
import com.liabletech.exportdocs.report.FixMessages;
import com.liabletech.exportdocs.report.ReportPdf;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.messaging.Messenger;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.messaging.Outgoing;
import com.liabletech.platform.numbering.DocumentNumbers;

/** Shipments and their documents: adding, correcting and re-checking them, and getting findings fixed. */
@Service
public class ShipmentService {

	private final Shipments shipments;

	private final ShipmentDocs docs;

	private final DocReader reader;

	private final DocData data;

	private final CheckService checks;

	private final Findings findings;

	private final DocumentNumbers numbers;

	private final FileStore files;

	private final Messenger messenger;

	private final Accounts accounts;

	private final TransactionTemplate transactions;

	private final Clock clock;

	public ShipmentService(Shipments shipments, ShipmentDocs docs, DocReader reader, DocData data, CheckService checks,
			Findings findings, DocumentNumbers numbers, FileStore files, Messenger messenger, Accounts accounts,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.shipments = shipments;
		this.docs = docs;
		this.reader = reader;
		this.data = data;
		this.checks = checks;
		this.findings = findings;
		this.numbers = numbers;
		this.files = files;
		this.messenger = messenger;
		this.accounts = accounts;
		this.transactions = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** A new shipment with the next reference of the financial year, e.g. EXP/2026-27/0012. */
	public long create(long accountId, String title) {
		Long id = transactions.execute((status) -> shipments.create(accountId,
				numbers.next(accountId, "EXP", LocalDate.now(clock)), StringUtils.hasText(title) ? title.trim() : null,
				OffsetDateTime.now(clock)));
		return id;
	}

	/** Reads an uploaded file into the shipment and checks the documents again. */
	public Added addDocument(long accountId, long shipmentId, DocType known, StoredFile file, String caption,
			String source) {
		shipments.get(accountId, shipmentId);
		return store(accountId, shipmentId, reader.read(accountId, known, file, caption), file, source);
	}

	/**
	 * A document from WhatsApp: it goes to the shipment whose LC number it quotes, or the one named in
	 * the caption, or the latest shipment still waiting for that kind of document. A credit nobody has
	 * seen before starts a new shipment.
	 */
	public Added receive(long accountId, StoredFile file, String caption) {
		DocReader.Reading reading = reader.read(accountId, null, file, caption);
		Shipment shipment = chooseShipment(accountId, reading, caption);
		return store(accountId, shipment.id(), reading, file, "WHATSAPP");
	}

	/** Stores a reading already made, e.g. for demo data. */
	public Added store(long accountId, long shipmentId, DocReader.Reading reading, StoredFile file, String source) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		long docId = docs.save(accountId, shipmentId, reading.type(), file == null ? null : file.id(), source,
				reading.status(), reading.note(), data.write(reading.data()), reading.confidence(), now);
		shipments.touch(shipmentId, now);
		if (reading.type() == DocType.LC && reading.data() instanceof LcReading lc) {
			Shipment shipment = shipments.get(accountId, shipmentId);
			if (shipment.title() == null) {
				String title = titleFor(lc);
				if (title != null) {
					shipments.updateDetails(accountId, shipmentId, withTitle(shipment, title), now);
				}
			}
		}
		CheckService.Result result = checks.check(accountId, shipmentId);
		return new Added(shipments.get(accountId, shipmentId), docs.get(accountId, docId), reading, result);
	}

	/** Saves the exporter's corrections to what was read, and checks again. */
	public void saveFields(long accountId, long docId, Map<String, String> params) {
		ShipmentDoc doc = docs.get(accountId, docId);
		Record current = data.read(doc);
		if (current == null) {
			return;
		}
		Record updated = apply(doc.type().readingType(), current, params);
		docs.updateData(docId, data.write(updated), ShipmentDoc.EDITED, OffsetDateTime.now(clock));
		checks.check(accountId, doc.shipmentId());
	}

	/** Reads the document again, as the given type when the first guess was wrong. */
	public void reread(long accountId, long docId, DocType as) {
		ShipmentDoc doc = docs.get(accountId, docId);
		if (doc.fileId() == null) {
			return;
		}
		StoredFile file = files.getForAccount(accountId, doc.fileId());
		DocType type = as != null ? as : doc.type();
		DocReader.Reading reading = reader.read(accountId, type, file, null);
		if (type != doc.type()) {
			docs.delete(accountId, docId);
		}
		store(accountId, doc.shipmentId(), reading, file, doc.source());
	}

	public void deleteDocument(long accountId, long docId) {
		ShipmentDoc doc = docs.get(accountId, docId);
		docs.delete(accountId, docId);
		checks.check(accountId, doc.shipmentId());
	}

	public void decide(long accountId, long findingId, boolean accept, String note) {
		Finding finding = findings.get(accountId, findingId);
		findings.setStatus(accountId, findingId, accept ? Finding.ACCEPTED : Finding.OPEN,
				StringUtils.hasText(note) ? note.trim() : null, OffsetDateTime.now(clock));
		checks.check(accountId, finding.shipmentId());
	}

	public void setStatus(long accountId, long shipmentId, String status) {
		Shipment shipment = shipments.get(accountId, shipmentId);
		OffsetDateTime now = OffsetDateTime.now(clock);
		switch (status) {
			case Shipment.SUBMITTED -> shipments.setStatus(accountId, shipmentId, Shipment.SUBMITTED, LocalDate.now(clock), now);
			case Shipment.PAID, Shipment.CANCELLED -> shipments.setStatus(accountId, shipmentId, status, shipment.submittedOn(), now);
			default -> {
				shipments.setStatus(accountId, shipmentId, Shipment.COLLECTING, null, now);
				checks.check(accountId, shipmentId);
			}
		}
	}

	/** Sends the forwarder the corrections the transport document needs, by WhatsApp and email. */
	public List<OutboundMessage> sendToForwarder(long accountId, long shipmentId) {
		Shipment shipment = shipments.get(accountId, shipmentId);
		List<Finding> fixes = FixMessages.forFixer(findings.forShipment(shipmentId), Fixer.FORWARDER);
		if (fixes.isEmpty()) {
			throw new IllegalStateException("There is nothing for the forwarder to correct.");
		}
		Account account = accounts.get(accountId);
		String text = FixMessages.forwarder(account, shipment, fixes);
		List<OutboundMessage> sent = new ArrayList<>();
		if (StringUtils.hasText(shipment.forwarderPhone())) {
			sent.add(messenger.send(Outgoing.whatsApp(accountId, shipment.forwarderPhone(), text)
				.about("shipment", shipmentId)
				.template("export_doc_corrections", shipment.forwarderName() == null ? "team" : shipment.forwarderName(),
						shipment.reference(), String.valueOf(fixes.size()), account.name())));
		}
		if (StringUtils.hasText(shipment.forwarderEmail())) {
			sent.add(messenger.send(Outgoing.email(accountId, shipment.forwarderEmail(),
					FixMessages.subjectForForwarder(account, shipment), text).about("shipment", shipmentId)));
		}
		if (sent.isEmpty()) {
			throw new IllegalStateException("Add the forwarder's WhatsApp number or email first.");
		}
		return sent;
	}

	/** Emails the buyer an amendment request listing the credit's terms that need changing. */
	public OutboundMessage sendToBuyer(long accountId, long shipmentId) {
		Shipment shipment = shipments.get(accountId, shipmentId);
		List<Finding> fixes = FixMessages.forFixer(findings.forShipment(shipmentId), Fixer.BUYER);
		if (fixes.isEmpty()) {
			throw new IllegalStateException("Nothing in the credit needs amending.");
		}
		if (!StringUtils.hasText(shipment.buyerEmail())) {
			throw new IllegalStateException("Add the buyer's email first.");
		}
		Account account = accounts.get(accountId);
		return messenger.send(Outgoing.email(accountId, shipment.buyerEmail(), FixMessages.subjectForBuyer(shipment),
				FixMessages.buyer(account, shipment, fixes)).about("shipment", shipmentId));
	}

	public byte[] report(long accountId, long shipmentId) {
		Shipment shipment = shipments.get(accountId, shipmentId);
		List<ShipmentDoc> all = docs.forShipment(shipmentId);
		Map<DocType, Record> readings = data.byType(all);
		LcTerms terms = readings.get(DocType.LC) instanceof LcReading lc ? LcTerms.of(lc) : null;
		return ReportPdf.render(accounts.get(accountId), shipment, terms, findings.forShipment(shipmentId),
				List.copyOf(readings.keySet()), LocalDate.now(clock));
	}

	private Shipment chooseShipment(long accountId, DocReader.Reading reading, String caption) {
		String lcNumber = lcNumberOf(reading.data());
		Optional<Shipment> match = shipments.findByLcNumber(accountId, lcNumber)
			.or(() -> shipments.findByReference(accountId, caption));
		if (match.isPresent()) {
			return match.get();
		}
		if (reading.type() != DocType.LC) {
			List<Shipment> active = shipments.list(accountId, true, 20);
			Optional<Shipment> waiting = active.stream()
				.filter((s) -> docs.find(s.id(), reading.type()).isEmpty())
				.findFirst();
			if (waiting.isPresent()) {
				return waiting.get();
			}
			if (!active.isEmpty()) {
				return active.getFirst();
			}
		}
		return shipments.get(accountId, create(accountId, null));
	}

	static String lcNumberOf(Record reading) {
		if (reading == null) {
			return null;
		}
		for (RecordComponent component : reading.getClass().getRecordComponents()) {
			if (component.getName().equals("lcNumber")) {
				Object value = Records.value(reading, component);
				return value == null || value.toString().isBlank() ? null : value.toString().trim();
			}
		}
		return null;
	}

	/** "Black pepper to Hamburg": the first words of 45A and the port of discharge. */
	static String titleFor(LcReading lc) {
		String goods = lc.goodsDescription() == null ? "" : lc.goodsDescription().strip().split("\\R")[0];
		goods = goods.replaceFirst("^[+\\-\\d.)\\s]+", "").replaceAll("[,;:(].*$", "").trim();
		String[] words = goods.split("\\s+");
		String what = Arrays.stream(words).limit(3).collect(Collectors.joining(" ")).toLowerCase(Locale.ROOT);
		String port = lc.portOfDischarge() == null ? "" : lc.portOfDischarge().split("[,(]")[0].trim().toLowerCase(Locale.ROOT);
		if (what.isBlank()) {
			return null;
		}
		String title = Character.toUpperCase(what.charAt(0)) + what.substring(1);
		if (!port.isBlank() && !port.startsWith("any")) {
			title += " to " + Arrays.stream(port.split("\\s+"))
				.map((w) -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
				.collect(Collectors.joining(" "));
		}
		return title.length() > 150 ? title.substring(0, 150) : title;
	}

	private static ShipmentForm withTitle(Shipment shipment, String title) {
		ShipmentForm form = ShipmentForm.of(shipment);
		form.setTitle(title);
		return form;
	}

	private static <T extends Record> T apply(Class<T> type, Record current, Map<String, String> params) {
		return DocFields.apply(type, type.cast(current), params);
	}

	/** A document added to a shipment, and how the check came out. */
	public record Added(Shipment shipment, ShipmentDoc doc, DocReader.Reading reading, CheckService.Result result) {
	}

}
