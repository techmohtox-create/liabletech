package com.liabletech.exportdocs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.liabletech.exportdocs.check.Finding;
import com.liabletech.exportdocs.check.Findings;
import com.liabletech.exportdocs.document.DocType;
import com.liabletech.exportdocs.document.ShipmentDoc;
import com.liabletech.exportdocs.document.ShipmentDocs;
import com.liabletech.exportdocs.reading.InsuranceReading;
import com.liabletech.exportdocs.reading.InvoiceReading;
import com.liabletech.exportdocs.reading.OriginReading;
import com.liabletech.exportdocs.reading.PackingListReading;
import com.liabletech.exportdocs.reading.TransportDocReading;
import com.liabletech.exportdocs.shipment.Shipment;
import com.liabletech.exportdocs.shipment.Shipments;
import com.liabletech.exportdocs.team.Senders;
import com.liabletech.exportdocs.whatsapp.SampleDocs;
import com.liabletech.exportdocs.whatsapp.SampleSet;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.account.PlanCatalog;
import com.liabletech.platform.account.SignupService;
import com.liabletech.platform.account.Users;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.messaging.Outbox;
import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.whatsapp.InboundMessages;
import com.liabletech.platform.whatsapp.InboundProcessor;
import com.liabletech.testing.MutableClock;
import com.liabletech.testing.StubDocumentAi;

/**
 * From the letter of credit to a presentation the bank will pay: documents uploaded or forwarded on
 * WhatsApp, read, checked against each other, corrected, and the corrections chased.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ExportTestConfig.class)
@TestPropertySource(locations = "classpath:export-test.properties")
class ExportFlowTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SignupService signups;

	@Autowired
	private Users users;

	@Autowired
	private Accounts accounts;

	@Autowired
	private PlanCatalog plans;

	@Autowired
	private Senders senders;

	@Autowired
	private Shipments shipments;

	@Autowired
	private ShipmentDocs docs;

	@Autowired
	private Findings findings;

	@Autowired
	private Outbox outbox;

	@Autowired
	private InboundMessages inbound;

	@Autowired
	private InboundProcessor processor;

	@Autowired
	private FileStore files;

	@Autowired
	private StubDocumentAi ai;

	@Autowired
	private MutableClock clock;

	private long account;

	private CurrentUser owner;

	private String ownerPhone;

	private String lcNumber;

	private SampleSet.Docs pepper;

	@BeforeEach
	void exporterWithAWhatsAppSender() {
		clock.set(ExportTestConfig.TODAY);
		ai.reset();
		long n = System.nanoTime() % 1_000_000;
		String email = "exporter" + n + "@example.com";
		ownerPhone = "+919" + String.format("%09d", n);
		account = signups.createDemo("Test Overseas", "Owner", ownerPhone, email, "password123");
		owner = new CurrentUser(users.findByEmail(email).orElseThrow());
		lcNumber = "HB/DC/26/" + n;
		pepper = SampleSet.pepper(ExportTestConfig.TODAY, lcNumber);
	}

	@Test
	void ownerCanSendDocumentsFromDayOne() {
		assertThat(senders.byPhone(ownerPhone)).hasValueSatisfying((s) -> assertThat(s.accountId()).isEqualTo(account));
	}

	@Test
	void swiftCreditIsReadWithoutAiAndTheInvoiceIsCheckedAgainstIt() throws Exception {
		long id = newShipment();

		upload(id, "LC", SampleDocs.lc(pepper.lc(), "HAFSDEHHXXX", "MLBCINBBXXX"), "lc.pdf");

		Shipment shipment = shipments.get(account, id);
		assertThat(ai.requests()).as("the MT700 text is parsed, not sent to the AI").isEmpty();
		assertThat(shipment.lcNumber()).isEqualTo(lcNumber);
		assertThat(shipment.lcAmount()).isEqualByComparingTo("85000");
		assertThat(shipment.lcExpiry()).isEqualTo(ExportTestConfig.TODAY.plusDays(14));
		assertThat(shipment.title()).isEqualTo("Indian black pepper to Hamburg");
		assertThat(docs.find(id, DocType.LC)).hasValueSatisfying((d) -> assertThat(d.readingStatus()).isEqualTo(ShipmentDoc.PARSED));
		assertThat(shipment.status()).isEqualTo(Shipment.COLLECTING);

		ai.willRead(InvoiceReading.class, pepper.invoice());
		upload(id, "INVOICE", SampleDocs.invoice(pepper.invoice()), "invoice.pdf");

		assertThat(open(id)).extracting(Finding::code).contains("INV_DESCRIPTION");
		assertThat(shipments.get(account, id).status()).isEqualTo(Shipment.CHECKED);
		assertThat(shipments.get(account, id).firstCheckedAt()).isNotNull();

		long invoiceDoc = docs.find(id, DocType.INVOICE).orElseThrow().id();
		mvc.perform(post("/shipments/" + id + "/documents/" + invoiceDoc).with(user(owner)).with(csrf())
			.param("f.goodsDescription", "INDIAN BLACK PEPPER ASTA GRADE 550 G/L, MACHINE CLEANED")
			.param("l.0.description", "BLACK PEPPER 550 G/L")
			.param("l.0.quantity", "20")
			.param("l.0.unit", "MT")
			.param("l.0.unitPrice", "4250.00")
			.param("l.0.amount", "85000.00")).andExpect(status().is3xxRedirection());

		assertThat(open(id)).extracting(Finding::code).doesNotContain("INV_DESCRIPTION");
		assertThat(findings.forShipment(id)).filteredOn((f) -> f.code().equals("INV_DESCRIPTION"))
			.singleElement()
			.satisfies((f) -> assertThat(f.status()).isEqualTo(Finding.FIXED));
		assertThat(docs.find(id, DocType.INVOICE).orElseThrow().readingStatus()).isEqualTo(ShipmentDoc.EDITED);
	}

	@Test
	void documentsForwardedOnWhatsAppAreSortedIntoTheShipmentAndAnswered() {
		sendFromOwner(SampleDocs.lc(pepper.lc(), "HAFSDEHHXXX", "MLBCINBBXXX"), "lc.pdf", "");

		Shipment shipment = shipments.findByLcNumber(account, lcNumber).orElseThrow();
		assertThat(lastReply().body()).contains("LC " + lcNumber).contains("USD 85,000.00").contains("6 documents required")
			.contains(shipment.reference());

		ai.willRead(InvoiceReading.class, pepper.invoice());
		ai.willRead(PackingListReading.class, pepper.packing());
		ai.willRead(TransportDocReading.class, pepper.transport());
		sendFromOwner(SampleDocs.invoice(pepper.invoice()), "inv.pdf", "");
		sendFromOwner(SampleDocs.packingList(pepper.packing()), "pl.pdf", "");
		sendFromOwner(SampleDocs.billOfLading(pepper.transport()), "bl.pdf", "BL draft");

		assertThat(docs.forShipment(shipment.id())).extracting(ShipmentDoc::docType)
			.containsExactlyInAnyOrder("LC", "INVOICE", "PACKING_LIST", "TRANSPORT");
		OutboundMessage reply = lastReply();
		assertThat(reply.body()).contains("Bill of lading / air waybill added to " + shipment.reference())
			.contains("discrepancies to fix before the bank")
			.contains("Shipped after the latest shipment date")
			.contains("Still needed: Insurance document, Certificate of origin")
			.contains("/shipments/" + shipment.id());
	}

	@Test
	void cleanDocumentsMakeTheShipmentReady() {
		SampleSet.Docs towels = SampleSet.towels(ExportTestConfig.TODAY, "GCB/LC/" + System.nanoTime() % 100000);
		sendFromOwner(SampleDocs.lc(towels.lc(), "GCBKAEADXXX", "MLBCINBBXXX"), "lc.pdf", "");
		ai.willRead(InvoiceReading.class, towels.invoice());
		ai.willRead(PackingListReading.class, towels.packing());
		ai.willRead(TransportDocReading.class, towels.transport());
		sendFromOwner(SampleDocs.invoice(towels.invoice()), "inv.pdf", "");
		sendFromOwner(SampleDocs.packingList(towels.packing()), "pl.pdf", "");
		sendFromOwner(SampleDocs.billOfLading(towels.transport()), "bl.pdf", "");

		Shipment shipment = shipments.findByLcNumber(account, towels.lc().lcNumber()).orElseThrow();
		assertThat(shipment.status()).isEqualTo(Shipment.READY);
		assertThat(shipment.criticalCount()).isZero();
		assertThat(lastReply().body()).contains("Ready for the bank");
	}

	@Test
	void correctionsGoToTheForwarderAndTheAmendmentRequestToTheBuyer() throws Exception {
		long id = fullPepperShipment();
		mvc.perform(post("/shipments/" + id).with(user(owner)).with(csrf())
			.param("forwarderName", "Seaways Logistics")
			.param("forwarderPhone", "9876543210")
			.param("forwarderEmail", "docs@seaways.example")
			.param("buyerEmail", "buyer@nordsee.example")).andExpect(status().is3xxRedirection());

		mvc.perform(post("/shipments/" + id + "/send/forwarder").with(user(owner)).with(csrf()))
			.andExpect(status().is3xxRedirection());
		mvc.perform(post("/shipments/" + id + "/send/buyer").with(user(owner)).with(csrf()))
			.andExpect(status().is3xxRedirection());

		List<OutboundMessage> sent = outbox.about(account, "shipment", id);
		OutboundMessage whatsApp = sent.stream().filter(OutboundMessage::isWhatsApp).findFirst().orElseThrow();
		assertThat(whatsApp.recipient()).isEqualTo("+919876543210");
		assertThat(whatsApp.templateName()).isEqualTo("export_doc_corrections");
		assertThat(whatsApp.body()).contains("Mark the bill of lading FREIGHT PREPAID")
			.contains("gross weight on the bill of lading / air waybill match the packing list (20,450 kg)");
		OutboundMessage toBuyer = sent.stream().filter((m) -> "buyer@nordsee.example".equals(m.recipient())).findFirst().orElseThrow();
		assertThat(toBuyer.subject()).isEqualTo("Request for amendment of LC " + lcNumber);
		assertThat(toBuyer.body()).contains("Extend the latest shipment date (44C)");
	}

	@Test
	void acceptingTheLastDiscrepanciesMakesTheShipmentReady() throws Exception {
		long id = fullPepperShipment();
		for (Finding finding : open(id)) {
			if (!finding.severity().equals("INFO")) {
				mvc.perform(post("/shipments/" + id + "/findings/" + finding.id()).with(user(owner)).with(csrf())
					.param("accept", "true")
					.param("note", "Buyer agreed to waive")).andExpect(status().is3xxRedirection());
			}
		}

		Shipment shipment = shipments.get(account, id);
		assertThat(shipment.criticalCount()).isZero();
		assertThat(shipment.status()).isEqualTo(Shipment.READY);

		mvc.perform(post("/shipments/" + id + "/check").with(user(owner)).with(csrf()));
		assertThat(shipments.get(account, id).status()).as("accepted findings stay accepted").isEqualTo(Shipment.READY);
	}

	@Test
	void reportIsAPdfWithTheFindings() throws Exception {
		long id = fullPepperShipment();

		byte[] pdf = mvc.perform(get("/shipments/" + id + "/report.pdf").with(user(owner)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		try (PdfReader reader = new PdfReader(pdf)) {
			assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
		}
		String text = com.liabletech.exportdocs.reading.PdfText.extract(pdf);
		assertThat(text).contains("5 discrepancies to fix before presenting").contains("UCP 600 art. 18(c)")
			.contains("Documents the credit asks for (46A)");
	}

	@Test
	void planCountsShipmentsCheckedThisMonth() throws Exception {
		fullPepperShipment();
		mvc.perform(post("/settings/plan").with(user(owner)).with(csrf()).param("planCode", "PER_SHIPMENT"))
			.andExpect(status().is3xxRedirection());

		PlanCatalog.PlanSummary summary = plans.summarize(accounts.get(account));

		assertThat(summary.planName()).isEqualTo("Per shipment");
		assertThat(summary.monthlyAmount()).isEqualByComparingTo("199");
	}

	@Test
	void unknownNumberIsToldToAskTheOffice() {
		String stranger = "+917" + String.format("%09d", System.nanoTime() % 1_000_000);
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(null, "wamid.x." + System.nanoTime(), stranger,
				"PLATFORM", null, "text", "status", null, null, null, null, null, OffsetDateTime.now(clock))).orElseThrow();
		processor.dispatch(id, stranger);

		assertThat(outbox.whatsAppToPhone(account, stranger, 5)).singleElement()
			.satisfies((m) -> assertThat(m.body()).contains("not set up to send export documents"));
	}

	@Test
	void everyPageRenders() throws Exception {
		long id = fullPepperShipment();
		long docId = docs.find(id, DocType.INVOICE).orElseThrow().id();
		long lcDoc = docs.find(id, DocType.LC).orElseThrow().id();
		for (String page : List.of("/", "/shipments", "/shipments?show=all", "/shipments/" + id,
				"/shipments/" + id + "/documents/" + docId, "/shipments/" + id + "/documents/" + lcDoc, "/team", "/settings",
				"/outbox", "/dev/whatsapp")) {
			mvc.perform(get(page).with(user(owner))).andExpect(status().isOk());
		}
	}

	private long fullPepperShipment() throws Exception {
		long id = newShipment();
		upload(id, "TRANSPORT", SampleDocs.billOfLading(pepper.transport()), "bl.pdf", TransportDocReading.class,
				pepper.transport());
		upload(id, "LC", SampleDocs.lc(pepper.lc(), "HAFSDEHHXXX", "MLBCINBBXXX"), "lc.pdf");
		upload(id, "PACKING_LIST", SampleDocs.packingList(pepper.packing()), "pl.pdf", PackingListReading.class,
				pepper.packing());
		upload(id, "INVOICE", SampleDocs.invoice(pepper.invoice()), "inv.pdf", InvoiceReading.class, pepper.invoice());
		upload(id, "INSURANCE", SampleDocs.insurance(pepper.insurance()), "ins.pdf", InsuranceReading.class,
				pepper.insurance());
		upload(id, "ORIGIN", SampleDocs.origin(pepper.origin()), "coo.pdf", OriginReading.class, pepper.origin());
		assertThat(open(id)).extracting(Finding::code)
			.contains("INV_DESCRIPTION", "BL_LATE_SHIPMENT", "BL_FREIGHT", "BL_GROSS_VS_PL", "INS_RISK");
		return id;
	}

	private long newShipment() throws Exception {
		String location = mvc.perform(post("/shipments").with(user(owner)).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andReturn()
			.getResponse()
			.getRedirectedUrl();
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	private <T> void upload(long id, String type, byte[] pdf, String name, Class<T> readingType, T reading)
			throws Exception {
		ai.willRead(readingType, reading);
		upload(id, type, pdf, name);
	}

	private void upload(long id, String type, byte[] pdf, String name) throws Exception {
		mvc.perform(multipart("/shipments/" + id + "/documents").file(new MockMultipartFile("file", name, "application/pdf", pdf))
			.param("type", type)
			.with(user(owner))
			.with(csrf())).andExpect(status().is3xxRedirection());
	}

	private void sendFromOwner(byte[] pdf, String filename, String caption) {
		long fileId = files.save(account, pdf, "application/pdf", filename).id();
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(null, "wamid.test." + System.nanoTime(), ownerPhone,
				"PLATFORM", "Owner", "document", caption, null, "application/pdf", filename, fileId, null,
				OffsetDateTime.now(clock))).orElseThrow();
		processor.dispatch(id, ownerPhone);
	}

	private OutboundMessage lastReply() {
		List<OutboundMessage> messages = outbox.whatsAppTo(account, ownerPhone, 20);
		assertThat(messages).isNotEmpty();
		return messages.getLast();
	}

	private List<Finding> open(long shipmentId) {
		return findings.forShipment(shipmentId).stream().filter(Finding::open).toList();
	}

}
