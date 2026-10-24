package com.liabletech.exportdocs.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.liabletech.exportdocs.document.DocReader;
import com.liabletech.exportdocs.document.DocType;
import com.liabletech.exportdocs.document.ShipmentDoc;
import com.liabletech.exportdocs.reading.InvoiceReading;
import com.liabletech.exportdocs.reading.LcReading;
import com.liabletech.exportdocs.reading.Mt700Parser;
import com.liabletech.exportdocs.reading.PackingListReading;
import com.liabletech.exportdocs.reading.PdfText;
import com.liabletech.exportdocs.shipment.Shipment;
import com.liabletech.exportdocs.shipment.ShipmentForm;
import com.liabletech.exportdocs.shipment.ShipmentService;
import com.liabletech.exportdocs.shipment.Shipments;
import com.liabletech.exportdocs.team.Senders;
import com.liabletech.exportdocs.whatsapp.ExportWhatsApp;
import com.liabletech.exportdocs.whatsapp.SampleDocs;
import com.liabletech.exportdocs.whatsapp.SampleSet;
import com.liabletech.platform.AppProperties;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.account.ProfileForm;
import com.liabletech.platform.account.SignupService;
import com.liabletech.platform.account.Users;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.messaging.Messenger;
import com.liabletech.platform.messaging.Outgoing;
import com.liabletech.platform.whatsapp.InboundMessage;
import com.liabletech.platform.whatsapp.InboundMessages;
import com.liabletech.platform.whatsapp.SimulatorController;

/**
 * A fictional merchant exporter in Kochi with four shipments: black pepper with five discrepancies
 * to fix, towels ready for the bank, brass handicrafts whose credit needs amending before shipping,
 * and a paid shipment. Every document is a generated PDF; the letters of credit are read by the
 * same MT700 parser as real ones.
 */
@Component
public class ExportDemoData implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ExportDemoData.class);

	private static final String HAFENSTADT = "HAFSDEHHXXX HAFENSTADT BANK AG, HAMBURG";

	private static final String ADVISING = "MLBCINBBXXX MALABAR COMMERCIAL BANK, KOCHI";

	private final AppProperties properties;

	private final Users users;

	private final SignupService signups;

	private final Accounts accounts;

	private final Senders senders;

	private final ShipmentService service;

	private final Shipments shipments;

	private final FileStore files;

	private final Messenger messenger;

	private final InboundMessages inbound;

	private final ExportWhatsApp whatsApp;

	private final Clock clock;

	public ExportDemoData(AppProperties properties, Users users, SignupService signups, Accounts accounts,
			Senders senders, ShipmentService service, Shipments shipments, FileStore files, Messenger messenger,
			InboundMessages inbound, ExportWhatsApp whatsApp, Clock clock) {
		this.properties = properties;
		this.users = users;
		this.signups = signups;
		this.accounts = accounts;
		this.senders = senders;
		this.service = service;
		this.shipments = shipments;
		this.files = files;
		this.messenger = messenger;
		this.inbound = inbound;
		this.whatsApp = whatsApp;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		AppProperties.Demo demo = properties.demo();
		if (!demo.enabled() || users.findByEmail(demo.email()).isPresent()) {
			return;
		}
		LocalDate today = LocalDate.now(clock);
		long account = signups.createDemo("Kaveri Overseas", "Anitha Menon", "+919000050000", demo.email(), demo.password());
		ProfileForm profile = ProfileForm.of(accounts.get(account));
		profile.setAddress("41/2104 MG Road, Ernakulam, Kochi 682016");
		profile.setGstin("32AAKFK4821M1Z9");
		accounts.updateProfile(account, profile);
		String rahul = "+919000050001";
		senders.add(account, "Rahul Nair (documentation)", rahul);

		// Documents go in the order they usually arrive at the desk: the bill of lading draft first, so the
		// check never sees a credit without its shipment and reports a passed shipment date.

		// Black pepper: the invoice first quoted a mistyped LC number and was corrected; five discrepancies remain.
		SampleSet.Docs pepper = SampleSet.pepper(today, "HB/DC/26/0917");
		long pepperId = shipment(account, "Black pepper to Hamburg", "Seaways Logistics, Kochi", "+919000060001",
				"docs@seaways.example", "purchasing@nordsee-spice.example");
		store(account, pepperId, DocType.TRANSPORT, pepper.transport(), SampleDocs.billOfLading(pepper.transport()),
				"bl-draft-0381.pdf");
		lc(account, pepperId, pepper.lc(), HAFENSTADT);
		store(account, pepperId, DocType.PACKING_LIST, pepper.packing(), SampleDocs.packingList(pepper.packing()),
				"packing-list-0142.pdf");
		InvoiceReading mistyped = withLcNumber(pepper.invoice(), "HB/DC/26/0971");
		store(account, pepperId, DocType.INVOICE, mistyped, SampleDocs.invoice(mistyped), "invoice-0142-draft.pdf");
		store(account, pepperId, DocType.INVOICE, pepper.invoice(), SampleDocs.invoice(pepper.invoice()), "invoice-0142.pdf");
		store(account, pepperId, DocType.INSURANCE, pepper.insurance(), SampleDocs.insurance(pepper.insurance()),
				"insurance-77514.pdf");
		store(account, pepperId, DocType.ORIGIN, pepper.origin(), SampleDocs.origin(pepper.origin()), "coo-58812.pdf");

		// Cotton towels: the packing list's gross weight was corrected; everything now agrees.
		SampleSet.Docs towels = SampleSet.towels(today, "GCB/LC/2026/3381");
		long towelsId = shipment(account, "Cotton bath towels to Jebel Ali", "Pearl Shipping Agency, Tuticorin",
				"+919000060002", "export@pearlshipping.example", "imports@alnoor-textiles.example");
		store(account, towelsId, DocType.TRANSPORT, towels.transport(), SampleDocs.billOfLading(towels.transport()),
				"bl-1187.pdf");
		lc(account, towelsId, towels.lc(), "GCBKAEADXXX GULF CRESCENT BANK PJSC, DUBAI");
		store(account, towelsId, DocType.INVOICE, towels.invoice(), SampleDocs.invoice(towels.invoice()), "invoice-0151.pdf");
		PackingListReading heavy = withGross(towels.packing(), "7500");
		store(account, towelsId, DocType.PACKING_LIST, heavy, SampleDocs.packingList(heavy), "packing-list-0151-draft.pdf");
		store(account, towelsId, DocType.PACKING_LIST, towels.packing(), SampleDocs.packingList(towels.packing()),
				"packing-list-0151.pdf");

		// Brass handicrafts: only the credit so far, sent on WhatsApp. Three terms are worth an amendment.
		LcReading brass = SampleSet.brass(today, "EMB/IMP/26/22015");
		long brassId = shipment(account, "Brass lamps to New York", null, null, null, "accounts@hudsonhome.example");
		StoredFile brassFile = files.save(account, SampleDocs.lc(brass, "EMBKUS33XXX EMPIRE MERCHANTS BANK, NEW YORK", ADVISING),
				"application/pdf", "LC-EMB-IMP-26-22015.pdf");
		OffsetDateTime sent = OffsetDateTime.now(clock).minusMinutes(3);
		long inboundId = inbound.insertIfNew(new InboundMessages.NewInbound(account, "demo-" + UUID.randomUUID(), rahul,
				SimulatorController.PLATFORM_NUMBER, "Rahul Nair", "document", "LC for the brass order", null,
				"application/pdf", brassFile.originalName(), brassFile.id(), null, sent)).orElseThrow();
		inbound.finish(inboundId, InboundMessage.PROCESSED, null, sent);
		ShipmentService.Added brassAdded = service.store(account, brassId, parsedLc(brassFile, brass), brassFile, "WHATSAPP");
		messenger.send(Outgoing.whatsApp(account, rahul, whatsApp.replyFor(brassAdded)).about("shipment", brassId));

		// An earlier towel shipment, presented on time and paid. It is marked presented first, so it is
		// checked as of the day it went to the bank.
		LocalDate earlier = today.minusDays(45);
		SampleSet.Docs paid = SampleSet.towels(earlier, "GCB/LC/2026/2954");
		long paidId = shipment(account, "Cotton bath towels to Jebel Ali (August)", "Pearl Shipping Agency, Tuticorin",
				"+919000060002", null, null);
		shipments.setStatus(account, paidId, Shipment.SUBMITTED, earlier.plusDays(4), OffsetDateTime.now(clock));
		store(account, paidId, DocType.TRANSPORT, paid.transport(), SampleDocs.billOfLading(paid.transport()), "bl-0877.pdf");
		lc(account, paidId, paid.lc(), "GCBKAEADXXX GULF CRESCENT BANK PJSC, DUBAI");
		store(account, paidId, DocType.INVOICE, paid.invoice(), SampleDocs.invoice(paid.invoice()), "invoice-0098.pdf");
		store(account, paidId, DocType.PACKING_LIST, paid.packing(), SampleDocs.packingList(paid.packing()), "pl-0098.pdf");
		service.setStatus(account, paidId, Shipment.PAID);

		log.info("Demo exporter created: sign in as {} / {}", demo.email(), demo.password());
	}

	private long shipment(long account, String title, String forwarder, String forwarderPhone, String forwarderEmail,
			String buyerEmail) {
		long id = service.create(account, title);
		ShipmentForm form = ShipmentForm.of(shipments.get(account, id));
		form.setForwarderName(forwarder);
		form.setForwarderPhone(forwarderPhone);
		form.setForwarderEmail(forwarderEmail);
		form.setBuyerEmail(buyerEmail);
		shipments.updateDetails(account, id, form, OffsetDateTime.now(clock));
		return id;
	}

	private void lc(long account, long shipmentId, LcReading lc, String sender) {
		StoredFile file = files.save(account, SampleDocs.lc(lc, sender, ADVISING), "application/pdf",
				"LC-" + lc.lcNumber().replace('/', '-') + ".pdf");
		service.store(account, shipmentId, parsedLc(file, lc), file, "UPLOAD");
	}

	/** The credit as the MT700 parser reads it from the generated PDF, as for a real upload. */
	private DocReader.Reading parsedLc(StoredFile file, LcReading fallback) {
		LcReading reading = Mt700Parser.parse(PdfText.extract(files.read(file))).orElse(fallback);
		return new DocReader.Reading(DocType.LC, reading, ShipmentDoc.PARSED, "Read from the SWIFT MT700 text.",
				BigDecimal.valueOf(reading.confidence()));
	}

	private void store(long account, long shipmentId, DocType type, Record reading, byte[] pdf, String filename) {
		StoredFile file = files.save(account, pdf, "application/pdf", filename);
		service.store(account, shipmentId, new DocReader.Reading(type, reading, ShipmentDoc.READ, null, new BigDecimal("0.94")),
				file, "UPLOAD");
	}

	private static InvoiceReading withLcNumber(InvoiceReading i, String lcNumber) {
		return new InvoiceReading(i.invoiceNumber(), i.invoiceDate(), i.seller(), i.buyer(), i.consignee(), lcNumber,
				i.orderReference(), i.currency(), i.incoterm(), i.incotermPlace(), i.portOfLoading(), i.portOfDischarge(),
				i.finalDestination(), i.countryOfOrigin(), i.vesselOrFlight(), i.goodsDescription(), i.lines(),
				i.totalQuantity(), i.quantityUnit(), i.totalAmount(), i.packages(), i.netWeightKg(), i.grossWeightKg(),
				i.marks(), i.signed(), i.confidence());
	}

	private static PackingListReading withGross(PackingListReading p, String gross) {
		return new PackingListReading(p.documentNumber(), p.documentDate(), p.invoiceNumber(), p.lcNumber(), p.seller(),
				p.buyer(), p.goodsDescription(),
				p.lines().stream()
					.map((l) -> new PackingListReading.PackingLine(l.description(), l.packages(), l.quantity(), l.unit(),
							l.netWeightKg(), gross))
					.toList(),
				p.totalPackages(), p.packageType(), p.totalQuantity(), p.quantityUnit(), p.netWeightKg(), gross,
				p.measurementCbm(), p.marks(), p.containerNumbers(), p.signed(), p.confidence());
	}

}
