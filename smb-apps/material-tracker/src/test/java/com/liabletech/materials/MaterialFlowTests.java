package com.liabletech.materials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.liabletech.materials.bill.Bill;
import com.liabletech.materials.bill.Bills;
import com.liabletech.materials.catalog.Material;
import com.liabletech.materials.catalog.Materials;
import com.liabletech.materials.catalog.Unit;
import com.liabletech.materials.delivery.Deliveries;
import com.liabletech.materials.delivery.Delivery;
import com.liabletech.materials.leakage.Flag;
import com.liabletech.materials.leakage.Flags;
import com.liabletech.materials.reading.MaterialDocReading;
import com.liabletech.materials.reading.MaterialDocReading.DocumentType;
import com.liabletech.materials.site.SiteMember;
import com.liabletech.materials.site.Sites;
import com.liabletech.materials.supplier.Suppliers;
import com.liabletech.materials.whatsapp.SlipSamples;
import com.liabletech.platform.account.SignupService;
import com.liabletech.platform.account.Users;
import com.liabletech.platform.ai.AiResult;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.messaging.Outbox;
import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.whatsapp.InboundMessages;
import com.liabletech.platform.whatsapp.InboundProcessor;
import com.liabletech.testing.MutableClock;
import com.liabletech.testing.StubDocumentAi;

/**
 * The leakage path: a challan photographed at the gate, the supervisor's count on WhatsApp, the
 * supplier's bill checked against both and the agreed rate, and the discrepancy raised and
 * recovered.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MaterialTestConfig.class)
@TestPropertySource(locations = "classpath:material-test.properties")
class MaterialFlowTests {

	private static final String SUPPLIER = "Shree Balaji Cement Traders";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SignupService signups;

	@Autowired
	private Users users;

	@Autowired
	private Sites sites;

	@Autowired
	private Suppliers suppliers;

	@Autowired
	private Materials materials;

	@Autowired
	private Deliveries deliveries;

	@Autowired
	private Bills bills;

	@Autowired
	private Flags flags;

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

	private long siteId;

	private long supplierId;

	private String supervisorPhone;

	private String supplierPhone;

	private Material opc;

	private Material steel12;

	@BeforeEach
	void siteWithASupervisorAndAgreedRates() {
		clock.set(MaterialTestConfig.START);
		ai.reset();
		long n = System.nanoTime() % 1_000_000;
		String email = "builder" + n + "@example.com";
		account = signups.createDemo("Test Builders", "Owner", "9" + String.format("%09d", n), email, "password123");
		owner = new CurrentUser(users.findByEmail(email).orElseThrow());
		siteId = sites.create(account, "Sai Heights", "Wagholi, Pune");
		supervisorPhone = "+918" + String.format("%09d", n);
		sites.addMember(account, siteId, "Santosh Patil", supervisorPhone, SiteMember.SUPERVISOR);
		supplierPhone = "+917" + String.format("%09d", n);
		supplierId = suppliers.create(account, SUPPLIER, supplierPhone, "accounts@balaji.example", "27AAKFS1234L1Z5");
		opc = material("Cement OPC 53");
		steel12 = material("TMT bar 12 mm");
		suppliers.addRate(account, supplierId, opc.id(), new BigDecimal("390"), Unit.BAG,
				MaterialTestConfig.START.minusMonths(2), null);
		suppliers.addRate(account, supplierId, steel12.id(), new BigDecimal("60000"), Unit.MT,
				MaterialTestConfig.START.minusMonths(2), null);
	}

	@Test
	void shortCountAtTheGateIsValuedAtTheAgreedRate() {
		ai.willRead(MaterialDocReading.class, challan("4401", line("UltraTech OPC 53 Grade", "200", "Bags")));

		sendPhotoFromSupervisor("");

		Delivery delivery = onlyDelivery();
		assertThat(delivery.status()).isEqualTo(Delivery.AWAITING_COUNT);
		assertThat(delivery.supplierId()).isEqualTo(supplierId);
		assertThat(deliveries.lines(delivery.id())).singleElement()
			.satisfies((l) -> assertThat(l.materialId()).isEqualTo(opc.id()));
		assertThat(lastReply().body()).contains("challan 4401").contains("1) UltraTech OPC 53 Grade – 200 bags")
			.contains("1=195");

		sendTextFromSupervisor("1=195");

		assertThat(deliveries.get(account, delivery.id()).status()).isEqualTo(Delivery.CONFIRMED);
		List<Flag> shortages = flags.forDelivery(account, delivery.id());
		assertThat(shortages).singleElement().satisfies((f) -> {
			assertThat(f.kind()).isEqualTo(Flag.SHORT_DELIVERY);
			assertThat(f.amount()).isEqualByComparingTo("1950");
		});
		assertThat(lastReply().body()).contains("Short at the gate").contains("5 bags short").contains("₹1,950");
	}

	@Test
	void okConfirmsTheSlipAsWritten() {
		ai.willRead(MaterialDocReading.class, challan("4402", line("OPC 53", "100", "bags")));
		sendPhotoFromSupervisor("");

		sendTextFromSupervisor("ok");

		Delivery delivery = onlyDelivery();
		assertThat(delivery.status()).isEqualTo(Delivery.CONFIRMED);
		assertThat(deliveries.lines(delivery.id()).getFirst().receivedQty()).isEqualByComparingTo("100");
		assertThat(flags.forDelivery(account, delivery.id())).isEmpty();
		assertThat(lastReply().body()).contains("everything arrived");
	}

	@Test
	void billChargingForMoreThanArrivedAndAboveTheAgreedRateIsCaughtRaisedAndRecovered() throws Exception {
		ai.willRead(MaterialDocReading.class, challan("4401", line("UltraTech OPC 53 Grade", "200", "Bags")));
		sendPhotoFromSupervisor("");
		sendTextFromSupervisor("1=195");

		// The supplier bills all 200 bags, at ₹405 instead of the agreed ₹390.
		ai.willRead(MaterialDocReading.class, bill("SBCT/1187", List.of("4401"), "81000", "22680", "103680",
				billLine("OPC 53 Grade Cement", "200", "Bags", "405", "81000", "4401")));
		sendPhotoFromSupervisor("bill");
		Bill bill = onlyBill();
		assertThat(bill.status()).isEqualTo(Bill.NEEDS_REVIEW);
		assertThat(lastReply().body()).contains("Bill received").contains("SBCT/1187");

		mvc.perform(post("/bills/" + bill.id() + "/check").with(user(owner)).with(csrf()))
			.andExpect(status().is3xxRedirection());

		List<Flag> findings = flags.forBill(account, bill.id());
		assertThat(findings).extracting(Flag::kind)
			.containsExactlyInAnyOrder(Flag.QTY_OVERBILLED, Flag.RATE_ABOVE_AGREED);
		assertThat(amountOf(findings, Flag.QTY_OVERBILLED)).as("5 bags at the agreed ₹390").isEqualByComparingTo("1950");
		assertThat(amountOf(findings, Flag.RATE_ABOVE_AGREED)).as("₹15 over on 200 bags").isEqualByComparingTo("3000");
		assertThat(bills.get(account, bill.id()).status()).isEqualTo(Bill.CHECKED);
		Delivery delivery = deliveries.list(account, siteId, null, 5).stream()
			.map((row) -> deliveries.get(account, row.id()))
			.findFirst()
			.orElseThrow();
		assertThat(flags.forDelivery(account, delivery.id())).as("the gate shortage now lives on the bill")
			.singleElement()
			.satisfies((f) -> assertThat(f.status()).isEqualTo(Flag.CLOSED));

		mvc.perform(post("/bills/" + bill.id() + "/raise").with(user(owner)).with(csrf()))
			.andExpect(status().is3xxRedirection());

		assertThat(bills.get(account, bill.id()).status()).isEqualTo(Bill.DISPUTED);
		assertThat(flags.forBill(account, bill.id())).allSatisfy((f) -> assertThat(f.status()).isEqualTo(Flag.RAISED));
		List<OutboundMessage> notes = outbox.about(account, "bill", bill.id())
			.stream()
			.filter((m) -> m.body().startsWith("Dear "))
			.toList();
		assertThat(notes).extracting(OutboundMessage::channel).containsExactlyInAnyOrder("WHATSAPP", "EMAIL");
		assertThat(notes).allSatisfy((m) -> {
			assertThat(m.body()).contains("SBCT/1187").contains("₹4,950");
			assertThat(m.attachmentIds()).containsExactly(bill.fileId());
		});

		mvc.perform(post("/bills/" + bill.id() + "/recovered").with(user(owner)).with(csrf()).param("note", "CN-77"))
			.andExpect(status().is3xxRedirection());

		assertThat(bills.get(account, bill.id()).status()).isEqualTo(Bill.APPROVED);
		assertThat(flags.forBill(account, bill.id())).allSatisfy((f) -> {
			assertThat(f.status()).isEqualTo(Flag.RECOVERED);
			assertThat(f.resolutionNote()).isEqualTo("CN-77");
		});
		assertThat(flags.totals(account, OffsetDateTime.now(clock).minusDays(30)).recovered())
			.isEqualByComparingTo("4950");
	}

	@Test
	void challanChargedOnASecondBillIsCaught() throws Exception {
		ai.willRead(MaterialDocReading.class, challan("4401", line("OPC 53", "100", "bags")));
		sendPhotoFromSupervisor("");
		sendTextFromSupervisor("ok");
		long first = billFromOffice("SBCT/1187", "4401", "100", "390", "39000");
		long second = billFromOffice("SBCT/1242", "4401", "100", "390", "39000");

		check(first);
		check(second);

		assertThat(flags.forBill(account, first)).isEmpty();
		List<Flag> findings = flags.forBill(account, second);
		assertThat(findings).extracting(Flag::kind)
			.containsExactlyInAnyOrder(Flag.DUPLICATE_CHALLAN, Flag.QTY_OVERBILLED);
		assertThat(amountOf(findings, Flag.QTY_OVERBILLED)).isEqualByComparingTo("39000");
		assertThat(amountOf(findings, Flag.DUPLICATE_CHALLAN)).as("the money is counted once").isZero();
		assertThat(findings).filteredOn((f) -> f.kind().equals(Flag.DUPLICATE_CHALLAN))
			.singleElement()
			.satisfies((f) -> assertThat(f.message()).contains("already charged on bill SBCT/1187"));
	}

	@Test
	void paddedLineAmountAndSteelBilledInTonnesAreCompared() throws Exception {
		ai.willRead(MaterialDocReading.class, challan("M-778", line("TMT Fe500D 12mm", "1250", "kg")));
		sendPhotoFromSupervisor("");
		sendTextFromSupervisor("ok");
		long billId = billFromOffice("MSH/552", "M-778", null, null, null);
		mvc.perform(post("/bills/" + billId).with(user(owner)).with(csrf())
			.param("supplierId", String.valueOf(supplierId))
			.param("siteId", String.valueOf(siteId))
			.param("billNumber", "MSH/552")
			.param("billDate", MaterialTestConfig.START.toString())
			.param("challanRefs", "M-778")
			.param("lines[0].materialId", String.valueOf(steel12.id()))
			.param("lines[0].description", "TMT 12 mm")
			.param("lines[0].qty", "1.28")
			.param("lines[0].unit", "MT")
			.param("lines[0].rate", "60000")
			.param("lines[0].amount", "77100")
			.param("check", "true")).andExpect(status().is3xxRedirection());

		List<Flag> findings = flags.forBill(account, billId);
		assertThat(findings).extracting(Flag::kind).containsExactlyInAnyOrder(Flag.QTY_OVERBILLED, Flag.ARITHMETIC);
		assertThat(amountOf(findings, Flag.QTY_OVERBILLED)).as("30 kg at ₹60/kg").isEqualByComparingTo("1800");
		assertThat(amountOf(findings, Flag.ARITHMETIC)).as("1.28 × 60,000 = 76,800").isEqualByComparingTo("300");
	}

	@Test
	void withoutAiTheSlipWaitsForTheOffice() throws Exception {
		ai.willFail(MaterialDocReading.class, AiResult.Outcome.DISABLED);

		sendPhotoFromSupervisor("cement 200 bags");

		Delivery delivery = onlyDelivery();
		assertThat(delivery.status()).isEqualTo(Delivery.NEEDS_REVIEW);
		assertThat(delivery.readingStatus()).isEqualTo(Delivery.MANUAL);
		assertThat(lastReply().body()).contains("The office will enter the quantities");

		mvc.perform(post("/deliveries/" + delivery.id()).with(user(owner)).with(csrf())
			.param("siteId", String.valueOf(siteId))
			.param("supplierId", String.valueOf(supplierId))
			.param("challanNumber", "4410")
			.param("lines[0].materialId", String.valueOf(opc.id()))
			.param("lines[0].slipQty", "200")
			.param("lines[0].unit", "BAG")
			.param("lines[0].receivedQty", "190")).andExpect(status().is3xxRedirection());

		assertThat(deliveries.get(account, delivery.id()).status()).isEqualTo(Delivery.CONFIRMED);
		assertThat(flags.forDelivery(account, delivery.id())).singleElement()
			.satisfies((f) -> assertThat(f.amount()).isEqualByComparingTo("3900"));
	}

	@Test
	void unknownNumberIsToldToAskTheOffice() {
		String stranger = "+916" + String.format("%09d", System.nanoTime() % 1_000_000);
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(null, "wamid.unknown." + System.nanoTime(), stranger,
				"PLATFORM", null, "text", "hello", null, null, null, null, null, OffsetDateTime.now(clock))).orElseThrow();
		processor.dispatch(id, stranger);

		assertThat(outbox.whatsAppToPhone(account, stranger, 5)).singleElement()
			.satisfies((m) -> assertThat(m.body()).contains("not set up for any site"));
	}

	@Test
	void everyPageRenders() throws Exception {
		ai.willRead(MaterialDocReading.class, challan("4401", line("OPC 53", "200", "bags")));
		sendPhotoFromSupervisor("");
		sendTextFromSupervisor("1=190");
		long billId = billFromOffice("SBCT/1187", "4401", "200", "405", "81000");
		check(billId);
		long deliveryId = onlyDelivery().id();
		for (String page : List.of("/", "/deliveries", "/deliveries?status=CONFIRMED&site=" + siteId,
				"/deliveries/" + deliveryId, "/bills", "/bills?status=CHECKED", "/bills/" + billId, "/leakage",
				"/leakage?status=ALL&supplier=" + supplierId, "/sites", "/sites/" + siteId, "/suppliers",
				"/suppliers/" + supplierId, "/materials", "/settings", "/outbox", "/dev/whatsapp")) {
			mvc.perform(get(page).with(user(owner))).andExpect(status().isOk());
		}
	}

	private long billFromOffice(String number, String challan, String qty, String rate, String amount) throws Exception {
		ai.willFail(MaterialDocReading.class, AiResult.Outcome.DISABLED);
		byte[] photo = SlipSamples.invoice(SUPPLIER, "27AAKFS1234L1Z5", number, MaterialTestConfig.START, "Test Builders",
				"Sai Heights", List.of(challan), List.of(new SlipSamples.Item("OPC 53", "100", "Bags", "390")), "39000",
				"10920", "49920");
		mvc.perform(multipart("/bills/upload").file(new MockMultipartFile("file", "bill.jpg", "image/jpeg", photo))
			.param("siteId", String.valueOf(siteId))
			.with(user(owner))
			.with(csrf())).andExpect(status().is3xxRedirection());
		long billId = bills.list(account, null, null, 1).getFirst().id();
		if (qty != null) {
			mvc.perform(post("/bills/" + billId).with(user(owner)).with(csrf())
				.param("supplierId", String.valueOf(supplierId))
				.param("siteId", String.valueOf(siteId))
				.param("billNumber", number)
				.param("billDate", MaterialTestConfig.START.toString())
				.param("challanRefs", challan)
				.param("lines[0].materialId", String.valueOf(opc.id()))
				.param("lines[0].description", "OPC 53")
				.param("lines[0].qty", qty)
				.param("lines[0].unit", "BAG")
				.param("lines[0].rate", rate)
				.param("lines[0].amount", amount)).andExpect(status().is3xxRedirection());
		}
		return billId;
	}

	private void check(long billId) throws Exception {
		mvc.perform(post("/bills/" + billId + "/check").with(user(owner)).with(csrf()))
			.andExpect(status().is3xxRedirection());
	}

	private void sendPhotoFromSupervisor(String caption) {
		byte[] photo = SlipSamples.challan(SUPPLIER, "27AAKFS1234L1Z5", "4401", MaterialTestConfig.START, "MH12AB1234",
				"Sai Heights", List.of(new SlipSamples.Item("OPC 53", "200", "Bags", null)));
		long fileId = files.save(account, photo, "image/jpeg", "slip.jpg").id();
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(null, "wamid.test." + System.nanoTime(),
				supervisorPhone, "PLATFORM", "Santosh", "image", caption, null, "image/jpeg", "slip.jpg", fileId, null,
				OffsetDateTime.now(clock))).orElseThrow();
		processor.dispatch(id, supervisorPhone);
	}

	private void sendTextFromSupervisor(String text) {
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(null, "wamid.test." + System.nanoTime(),
				supervisorPhone, "PLATFORM", "Santosh", "text", text, null, null, null, null, null,
				OffsetDateTime.now(clock))).orElseThrow();
		processor.dispatch(id, supervisorPhone);
	}

	private OutboundMessage lastReply() {
		List<OutboundMessage> messages = outbox.whatsAppTo(account, supervisorPhone, 20);
		assertThat(messages).isNotEmpty();
		return messages.getLast();
	}

	private Delivery onlyDelivery() {
		return deliveries.list(account, siteId, null, 5).stream()
			.map((row) -> deliveries.get(account, row.id()))
			.reduce((a, b) -> {
				throw new AssertionError("more than one delivery");
			})
			.orElseThrow();
	}

	private Bill onlyBill() {
		List<Bills.BillRow> rows = bills.list(account, null, null, 5);
		assertThat(rows).hasSize(1);
		return bills.get(account, rows.getFirst().id());
	}

	private Material material(String name) {
		return materials.all(account).stream().filter((m) -> m.name().equals(name)).findFirst().orElseThrow();
	}

	private static BigDecimal amountOf(List<Flag> flags, String kind) {
		return flags.stream().filter((f) -> f.kind().equals(kind)).map(Flag::amount).reduce(BigDecimal.ZERO,
				BigDecimal::add);
	}

	private static MaterialDocReading.Line line(String description, String quantity, String unit) {
		return new MaterialDocReading.Line(description, quantity, unit, "", "", "");
	}

	private static MaterialDocReading.Line billLine(String description, String quantity, String unit, String rate,
			String amount, String challan) {
		return new MaterialDocReading.Line(description, quantity, unit, rate, amount, challan);
	}

	private static MaterialDocReading challan(String number, MaterialDocReading.Line... lines) {
		return new MaterialDocReading(DocumentType.DELIVERY_CHALLAN, SUPPLIER, "27AAKFS1234L1Z5", number, "2026-10-01",
				"MH12AB1234", List.of(lines), List.of(), "", "", "", 0.93);
	}

	private static MaterialDocReading bill(String number, List<String> challans, String taxable, String tax, String total,
			MaterialDocReading.Line... lines) {
		return new MaterialDocReading(DocumentType.TAX_INVOICE, SUPPLIER, "27AAKFS1234L1Z5", number, "2026-10-01", "",
				List.of(lines), challans, taxable, tax, total, 0.95);
	}

}
