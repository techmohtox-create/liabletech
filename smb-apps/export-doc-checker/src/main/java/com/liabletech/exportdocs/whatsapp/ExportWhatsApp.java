package com.liabletech.exportdocs.whatsapp;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.liabletech.exportdocs.check.Finding;
import com.liabletech.exportdocs.check.Findings;
import com.liabletech.exportdocs.check.LcTerms;
import com.liabletech.exportdocs.check.Severity;
import com.liabletech.exportdocs.check.TradeText;
import com.liabletech.exportdocs.document.DocType;
import com.liabletech.exportdocs.reading.LcReading;
import com.liabletech.exportdocs.shipment.Shipment;
import com.liabletech.exportdocs.shipment.ShipmentService;
import com.liabletech.exportdocs.shipment.Shipments;
import com.liabletech.exportdocs.team.DocSender;
import com.liabletech.exportdocs.team.Senders;
import com.liabletech.platform.AppProperties;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.whatsapp.InboundContext;
import com.liabletech.platform.whatsapp.InboundHandler;
import com.liabletech.platform.whatsapp.InboundMessage;
import com.liabletech.platform.whatsapp.InboundMessages;
import com.liabletech.platform.whatsapp.InboundRouter;
import com.liabletech.platform.whatsapp.SimulatorDirectory;

/**
 * The export team forwards the LC and each document as it arrives; the reply says what was read and
 * what to fix before going to the bank.
 */
@Component
public class ExportWhatsApp implements InboundRouter, InboundHandler, SimulatorDirectory {

	static final String HELP = """
			Forward the letter of credit first, then the invoice, packing list, bill of lading and other documents \
			as PDFs or photos. Each one is checked against the LC and the others.
			Type STATUS to see the shipments in progress.""";

	private static final int MAX_LISTED = 5;

	private final Senders senders;

	private final ShipmentService service;

	private final Shipments shipments;

	private final Findings findings;

	private final InboundMessages inbound;

	private final AppProperties properties;

	private final Clock clock;

	public ExportWhatsApp(Senders senders, ShipmentService service, Shipments shipments, Findings findings,
			InboundMessages inbound, AppProperties properties, Clock clock) {
		this.senders = senders;
		this.service = service;
		this.shipments = shipments;
		this.findings = findings;
		this.inbound = inbound;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public Optional<Long> accountFor(String fromPhone) {
		return senders.byPhone(fromPhone).filter(DocSender::active).map(DocSender::accountId);
	}

	@Override
	public void handle(InboundContext context) {
		InboundMessage message = context.message();
		if (senders.byPhone(context.from()).filter((s) -> s.accountId().equals(context.accountId())).isEmpty()) {
			context.reply(replyToUnknownSender(message));
			return;
		}
		if (message.isPhotoOrPdf()) {
			Optional<StoredFile> file = context.file();
			if (file.isEmpty()) {
				context.reply("We could not open that file. Please send the document as a PDF or a clear photo.");
				return;
			}
			ShipmentService.Added added = service.receive(context.accountId(), file.get(), message.text());
			context.reply(replyFor(added), "shipment", added.shipment().id());
			return;
		}
		String text = message.text().toLowerCase(Locale.ROOT).trim();
		if (text.startsWith("status") || text.equals("list") || text.equals("s")) {
			context.reply(status(context.accountId()));
			return;
		}
		context.reply(HELP);
	}

	@Override
	public String replyToUnknownSender(InboundMessage message) {
		return "This number is not set up to send export documents. Ask the office to add it under Team in Export Doc Checker.";
	}

	public String replyFor(ShipmentService.Added added) {
		Shipment shipment = added.shipment();
		DocType type = added.reading().type();
		StringBuilder text = new StringBuilder();
		if (type == DocType.OTHER) {
			return "📎 Kept with " + shipment.reference() + ", but this does not look like an LC, invoice, packing list, "
					+ "bill of lading, certificate of origin or insurance document, so it was not checked.";
		}
		if (added.doc().needsTyping()) {
			text.append("📄 ").append(type.label()).append(" added to ").append(shipment.reference())
				.append(". It could not be read automatically: open ").append(link(shipment))
				.append(" to type in the key fields.");
			return text.toString();
		}
		if (type == DocType.LC && added.reading().data() instanceof LcReading lc) {
			LcTerms terms = LcTerms.of(lc);
			text.append("🏦 LC ").append(nz(terms.number(), "(no number)"));
			if (TradeText.firstLine(lc.issuingBank()) != null) {
				text.append(" from ").append(TradeText.firstLine(lc.issuingBank()));
			}
			text.append(" read: ").append(TradeText.money(terms.currency(), terms.amount()));
			if (terms.latestShipment() != null) {
				text.append(", ship by ").append(TradeText.show(terms.latestShipment()));
			}
			if (terms.expiry() != null) {
				text.append(", expires ").append(TradeText.show(terms.expiry()));
				if (terms.expiryPlace() != null) {
					text.append(" in ").append(terms.expiryPlace());
				}
			}
			text.append(". ").append(terms.requiredDocs().size()).append(" documents required. Shipment ")
				.append(shipment.reference()).append('.');
		}
		else {
			text.append("📄 ").append(type.label()).append(" added to ").append(shipment.reference());
			if (shipment.lcNumber() != null) {
				text.append(" (LC ").append(shipment.lcNumber()).append(')');
			}
			text.append('.');
		}
		List<Finding> open = findings.forShipment(shipment.id())
			.stream()
			.filter((f) -> f.open() && f.level() != Severity.INFO)
			.toList();
		long critical = open.stream().filter((f) -> f.level() == Severity.CRITICAL).count();
		if (!open.isEmpty()) {
			text.append("\n\n").append(critical > 0
					? "❌ " + critical + (critical == 1 ? " discrepancy" : " discrepancies") + " to fix before the bank:"
					: "⚠️ " + open.size() + (open.size() == 1 ? " point" : " points") + " to check:");
			int n = 1;
			for (Finding finding : open.subList(0, Math.min(open.size(), MAX_LISTED))) {
				text.append('\n').append(n++).append(". ").append(finding.title());
				if (finding.fix() != null) {
					text.append(" → ").append(finding.fix());
				}
			}
			if (open.size() > MAX_LISTED) {
				text.append("\n…and ").append(open.size() - MAX_LISTED).append(" more.");
			}
		}
		else if (Shipment.READY.equals(shipment.status())) {
			text.append("\n\n✅ All documents agree with the LC and with each other. Ready for the bank.");
		}
		if (!added.result().missing().isEmpty()) {
			text.append("\n\nStill needed: ").append(String.join(", ", added.result().missing())).append('.');
		}
		text.append("\nDetails: ").append(link(shipment));
		return text.toString();
	}

	private String status(long accountId) {
		List<Shipment> active = shipments.list(accountId, true, 10);
		if (active.isEmpty()) {
			return "No shipments in progress. Forward a letter of credit to start one.";
		}
		LocalDate today = LocalDate.now(clock);
		StringBuilder text = new StringBuilder("Shipments in progress:");
		for (Shipment shipment : active) {
			text.append("\n• ").append(shipment.reference()).append(' ').append(shipment.displayName().equals(shipment.reference())
					? "" : shipment.displayName());
			if (shipment.criticalCount() > 0) {
				text.append(" – ❌ ").append(shipment.criticalCount()).append(" to fix");
			}
			else if (Shipment.READY.equals(shipment.status())) {
				text.append(" – ✅ ready");
			}
			Shipment.Deadline deadline = shipment.nextDeadline(today);
			if (deadline != null) {
				text.append(" · ").append(deadline.label().toLowerCase(Locale.ROOT)).append(' ')
					.append(TradeText.show(deadline.date()));
			}
		}
		return text.toString();
	}

	private String link(Shipment shipment) {
		return properties.baseUrl() + "/shipments/" + shipment.id();
	}

	/** Active senders, the one who wrote last first: that is the conversation to look at. */
	@Override
	public List<Contact> contacts(long accountId) {
		List<String> recent = inbound.recentForAccount(accountId, 50)
			.stream()
			.map(InboundMessage::fromPhone)
			.distinct()
			.toList();
		List<DocSender> active = new ArrayList<>(senders.all(accountId).stream().filter(DocSender::active).toList());
		active.sort(Comparator.comparingInt((DocSender s) -> recent.contains(s.phone()) ? recent.indexOf(s.phone())
				: Integer.MAX_VALUE));
		return active.stream().map((s) -> new Contact(s.name(), s.phone(), "Export documents")).toList();
	}

	/**
	 * A pepper credit with a number of its own for the day, so the samples start a new shipment
	 * instead of updating the demo one, and the documents sent after it quote the same number.
	 */
	@Override
	public List<Sample> samples(long accountId) {
		LocalDate today = LocalDate.now(clock);
		String lcNumber = "HB/DC/26/" + (5000 + Math.floorMod(accountId * 37 + today.getDayOfYear(), 4000));
		SampleSet.Docs docs = SampleSet.pepper(today, lcNumber);
		return List.of(
				new Sample("lc", "Letter of credit (MT700)", "LC",
						() -> new SampleFile(SampleDocs.lc(docs.lc(), "HAFSDEHHXXX HAFENSTADT BANK AG, HAMBURG",
								"MLBCINBBXXX MALABAR COMMERCIAL BANK, KOCHI"), "application/pdf", "LC-" + lcNumber.replace('/', '-') + ".pdf")),
				new Sample("invoice", "Commercial invoice", "Invoice",
						() -> new SampleFile(SampleDocs.invoice(docs.invoice()), "application/pdf", "invoice.pdf")),
				new Sample("packing", "Packing list", "PL",
						() -> new SampleFile(SampleDocs.packingList(docs.packing()), "application/pdf", "packing-list.pdf")),
				new Sample("bl", "Bill of lading", "BL",
						() -> new SampleFile(SampleDocs.billOfLading(docs.transport()), "application/pdf", "bill-of-lading.pdf")));
	}

	private static String nz(String value, String fallback) {
		return value == null ? fallback : value;
	}

}
