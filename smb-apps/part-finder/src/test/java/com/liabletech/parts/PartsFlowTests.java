package com.liabletech.parts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

import com.liabletech.parts.booking.Booking;
import com.liabletech.parts.booking.Bookings;
import com.liabletech.parts.catalog.Part;
import com.liabletech.parts.catalog.PartType;
import com.liabletech.parts.catalog.Parts;
import com.liabletech.parts.catalog.StockCsv;
import com.liabletech.parts.enquiry.Enquiries;
import com.liabletech.parts.enquiry.Enquiry;
import com.liabletech.parts.enquiry.EnquiryService;
import com.liabletech.parts.enquiry.Identification;
import com.liabletech.parts.enquiry.PartReading;
import com.liabletech.parts.enquiry.QuoteOptions;
import com.liabletech.parts.mechanic.Mechanic;
import com.liabletech.parts.mechanic.Mechanics;
import com.liabletech.parts.report.Reports;
import com.liabletech.parts.settings.ShopSettingsRepository;
import com.liabletech.parts.whatsapp.PartSamples;
import com.liabletech.platform.account.Accounts;
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
 * From a mechanic's WhatsApp to a booked part: understood, matched to stock, answered (by the
 * counter, or straight away on Pro), booked with "1", and counted in the reports.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PartsTestConfig.class)
@TestPropertySource(locations = "classpath:parts-test.properties")
class PartsFlowTests {

	private static final String STOCK = """
			part_number,name,category,brand,price,mrp,stock,rack,fits,alt_numbers
			BP-SWF-PS,Front brake pad set,BRAKE_PAD,ProStop,1150,1450,6,B3,Swift 2018-2024; Dzire 2017-2024,PS-1123
			BP-SWF-GEN,Front brake pad set,BRAKE_PAD,Genuine,1890,2150,2,B3,Swift 2018-2024; Dzire 2017-2024,
			BP-I20-F,Front brake pad set,BRAKE_PAD,ProStop,1250,1590,1,B3,i20 2015-2023,
			CP-BOL,Clutch plate,CLUTCH_PLATE,DuraClutch,2450,3050,0,C2,Bolero 2011-2024,
			CK-BOL,"Clutch kit (plate, cover, bearing)",CLUTCH_KIT,DuraClutch,6900,8400,1,C2,Bolero 2011-2024,
			OF-MS-110,Oil filter,OIL_FILTER,Filtra,180,240,40,F1,Swift; Dzire; Alto,
			""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SignupService signups;

	@Autowired
	private Users users;

	@Autowired
	private Accounts accounts;

	@Autowired
	private StockCsv stockCsv;

	@Autowired
	private Parts parts;

	@Autowired
	private Mechanics mechanics;

	@Autowired
	private Enquiries enquiries;

	@Autowired
	private EnquiryService service;

	@Autowired
	private QuoteOptions options;

	@Autowired
	private Bookings bookings;

	@Autowired
	private ShopSettingsRepository settings;

	@Autowired
	private Reports reports;

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
	private PartsTestConfig.FakeSpeechToText speech;

	@Autowired
	private MutableClock clock;

	private long account;

	private CurrentUser owner;

	private String ownerPhone;

	private String mechanicPhone;

	private long n;

	@BeforeEach
	void shopWithStockAndAMechanic() {
		clock.set(PartsTestConfig.TODAY);
		ai.reset();
		speech.willHear(null);
		n = System.nanoTime() % 1_000_000;
		String email = "parts" + n + "@example.com";
		ownerPhone = "+919" + String.format("%09d", n);
		mechanicPhone = "+918" + String.format("%09d", n);
		account = signups.createDemo("Test Auto Spares", "Owner", ownerPhone, email, "password123");
		owner = new CurrentUser(users.findByEmail(email).orElseThrow());
		stockCsv.importCsv(account, STOCK.getBytes(StandardCharsets.UTF_8));
		mechanics.findOrCreate(account, mechanicPhone, "Raju", OffsetDateTime.now(clock));
	}

	@Test
	void newShopStartsOnCounterWithTheStandardAnswers() {
		assertThat(accounts.get(account).planCode()).isEqualTo(PartsNavigation.COUNTER);
		assertThat(settings.get(account).acknowledge()).isTrue();
		assertThat(parts.countActive(account)).isEqualTo(6);
	}

	@Test
	void counterQuotesAMessageAndTheMechanicBooksWithOne() throws Exception {
		fromMechanic("Swift front brake pad chahiye");

		Enquiry enquiry = latest();
		assertThat(enquiry.status()).isEqualTo(Enquiry.NEW);
		assertThat(enquiry.readingStatus()).isEqualTo(Identification.PARSED);
		assertThat(lastToMechanic().body()).startsWith("👍 Got it: Swift front brake pads. Checking stock");
		mvc.perform(get("/inbox").with(user(owner))).andExpect(status().isOk());

		Part pads = parts.byNumber(account, "BP-SWF-PS").orElseThrow();
		clock.advance(Duration.ofSeconds(48));
		mvc.perform(post("/enquiries/" + enquiry.id() + "/quote").with(user(owner)).with(csrf())
			.param("partId", String.valueOf(pads.id()))
			.param("price_" + pads.id(), "1100")).andExpect(status().is3xxRedirection());

		Enquiry quoted = enquiries.get(account, enquiry.id());
		assertThat(quoted.status()).isEqualTo(Enquiry.QUOTED);
		assertThat(quoted.repliedBy()).isEqualTo(Enquiry.STAFF);
		assertThat(quoted.responseSeconds()).isEqualTo(48);
		assertThat(lastToMechanic().body()).contains("ProStop front brake pad set – ₹1,100").contains("Reply 1 to book.");

		fromMechanic("1");

		assertThat(enquiries.get(account, enquiry.id()).status()).isEqualTo(Enquiry.BOOKED);
		assertThat(parts.get(account, pads.id()).stock()).as("set aside").isEqualTo(5);
		assertThat(lastToMechanic().body()).startsWith("✅ Booked: ProStop front brake pad set, ₹1,100.");

		fromMechanic("cancel");

		assertThat(parts.get(account, pads.id()).stock()).as("back on the shelf").isEqualTo(6);
		assertThat(bookings.forEnquiry(enquiry.id())).hasValueSatisfying((b) -> assertThat(b.status()).isEqualTo(Booking.CANCELLED));
		assertThat(lastToMechanic().body()).startsWith("Cancelled: ProStop front brake pad set.");
	}

	@Test
	void proQuotesASureMatchInSeconds() {
		accounts.setPlan(account, PartsNavigation.PRO);

		fromMechanic("Swift 2019 front brake pad");

		Enquiry enquiry = latest();
		assertThat(enquiry.status()).isEqualTo(Enquiry.QUOTED);
		assertThat(enquiry.repliedBy()).isEqualTo(Enquiry.AUTO);
		assertThat(enquiry.responseSeconds()).isZero();
		assertThat(options.forEnquiry(enquiry.id())).extracting(QuoteOptions.QuoteOption::description)
			.containsExactly("ProStop front brake pad set", "Genuine front brake pad set");
		assertThat(lastToMechanic().body()).contains("1) ProStop front brake pad set – ₹1,150")
			.contains("2) Genuine front brake pad set – ₹1,890")
			.contains("Reply 1 or 2 to book.");

		fromMechanic("2 wala");

		assertThat(bookings.forEnquiry(enquiry.id())).hasValueSatisfying((b) -> {
			assertThat(b.description()).isEqualTo("Genuine front brake pad set");
			assertThat(b.price()).isEqualByComparingTo("1890");
		});
	}

	@Test
	void theQuestionGoesFirstAndTheAnswerCompletesTheEnquiry() {
		accounts.setPlan(account, PartsNavigation.PRO);

		fromMechanic("i20 brake pad hai kya?");

		assertThat(lastToMechanic().body()).contains("i20 brake pads. Front or rear?");
		assertThat(latest().status()).as("not sure without the side").isEqualTo(Enquiry.NEW);

		clock.advance(Duration.ofSeconds(50));
		fromMechanic("front");

		Enquiry enquiry = latest();
		assertThat(enquiries.list(account, List.of(Enquiry.NEW, Enquiry.QUOTED), null, 10)).as("one enquiry, not two")
			.hasSize(1);
		assertThat(enquiry.status()).isEqualTo(Enquiry.QUOTED);
		assertThat(enquiry.position()).isEqualTo("FRONT");
		assertThat(enquiry.text()).isEqualTo("i20 brake pad hai kya? / front");
		assertThat(lastToMechanic().body()).contains("ProStop front brake pad set – ₹1,250");
	}

	@Test
	void photoIsReadAndANumberOnItFindsThePart() throws Exception {
		ai.willRead(PartReading.class, new PartReading("BRAKE_PAD", "Front disc brake pad set", List.of("PS-1123"), "ProStop",
				"Maruti Suzuki", "Swift", "", "FRONT", 1, "", 0.92));

		photoFromMechanic("ye wala chahiye");

		Enquiry enquiry = latest();
		assertThat(enquiry.kind()).isEqualTo(Enquiry.PHOTO);
		assertThat(enquiry.readingStatus()).isEqualTo(Identification.READ);
		assertThat(enquiry.partNumbers()).isEqualTo("PS-1123");
		assertThat(service.candidates(enquiry).getFirst()).satisfies((c) -> {
			assertThat(c.part().partNumber()).isEqualTo("BP-SWF-PS");
			assertThat(c.reasons()).contains("number matches");
		});
		String inbox = mvc.perform(get("/inbox").with(user(owner))).andReturn().getResponse().getContentAsString();
		assertThat(inbox).contains("Looks like: Swift front brake pads").contains("Numbers seen: PS-1123");
		mvc.perform(get("/enquiries/" + enquiry.id()).with(user(owner))).andExpect(status().isOk());
	}

	@Test
	void photoTheAiCannotReadWaitsForTheCounter() {
		ai.willFail(PartReading.class, AiResult.Outcome.FAILED);

		photoFromMechanic(null);

		Enquiry enquiry = latest();
		assertThat(enquiry.readingStatus()).isEqualTo(Identification.MANUAL);
		assertThat(enquiry.readingNote()).startsWith("The photo could not be read");
		assertThat(lastToMechanic().body()).startsWith("👍 Got it. Checking stock");
	}

	@Test
	void voiceNoteIsTranscribedWhenSpeechToTextIsSetUp() {
		voiceFromMechanic();
		assertThat(latest().readingNote()).isEqualTo("Voice note: play it at the counter.");

		speech.willHear("Bolero clutch plate chahiye, 2016 model");
		voiceFromMechanic();

		Enquiry enquiry = latest();
		assertThat(enquiry.kind()).isEqualTo(Enquiry.VOICE);
		assertThat(enquiry.transcript()).isEqualTo("Bolero clutch plate chahiye, 2016 model");
		assertThat(enquiry.partType()).isEqualTo(PartType.CLUTCH_PLATE.name());
		assertThat(enquiry.vehicleYear()).isEqualTo(2016);
		assertThat(service.candidates(enquiry)).extracting((c) -> c.part().partNumber()).containsExactly("CP-BOL", "CK-BOL");
	}

	@Test
	void notInStockIsToldAndCountedAsMissedDemand() throws Exception {
		fromMechanic("Bolero clutch plate urgent");
		Enquiry enquiry = latest();

		mvc.perform(post("/enquiries/" + enquiry.id() + "/unavailable").with(user(owner)).with(csrf())
			.param("note", "We can get it by tomorrow 11 am.")).andExpect(status().is3xxRedirection());

		assertThat(enquiries.get(account, enquiry.id()).status()).isEqualTo(Enquiry.NOT_AVAILABLE);
		assertThat(lastToMechanic().body())
			.startsWith("❌ Sorry, Bolero clutch plate is not in stock right now. We can get it by tomorrow 11 am.");
		assertThat(reports.missed(account, OffsetDateTime.now(clock).minusDays(1), 10)).singleElement().satisfies((m) -> {
			assertThat(m.part()).isEqualTo("Clutch plate");
			assertThat(m.vehicle()).isEqualTo("Bolero");
		});
		mvc.perform(get("/reports").with(user(owner))).andExpect(status().isOk());
	}

	@Test
	void soldOutBeforeBookingGoesBackToTheCounter() {
		accounts.setPlan(account, PartsNavigation.PRO);
		fromMechanic("i20 2017 front brake pad");
		Enquiry enquiry = latest();
		assertThat(enquiry.status()).isEqualTo(Enquiry.QUOTED);
		Part pads = parts.byNumber(account, "BP-I20-F").orElseThrow();
		parts.setStock(account, pads.id(), 0, OffsetDateTime.now(clock));

		fromMechanic("1");

		assertThat(lastToMechanic().body()).contains("just gone out of stock");
		assertThat(enquiries.get(account, enquiry.id()).status()).isEqualTo(Enquiry.NEW);
		assertThat(bookings.forEnquiry(enquiry.id())).isEmpty();
	}

	@Test
	void ownerIsToldOnceWhenAnEnquiryWaits() {
		accounts.setPlan(account, PartsNavigation.PRO);
		fromMechanic("Bolero clutch plate");

		clock.advance(Duration.ofMinutes(2));
		service.nudge();
		assertThat(outbox.whatsAppToPhone(account, ownerPhone, 5)).as("not yet: 3 minutes allowed").isEmpty();

		clock.advance(Duration.ofMinutes(2));
		service.nudge();
		service.nudge();

		assertThat(outbox.whatsAppToPhone(account, ownerPhone, 5)).singleElement().satisfies((m) -> {
			assertThat(m.templateName()).isEqualTo("parts_waiting_nudge");
			assertThat(m.body()).startsWith("⏱ Raju has waited 4 min for Bolero clutch plate.");
		});
	}

	@Test
	void quotesCloseAfterADayAndABareNumberIsAnswered() {
		accounts.setPlan(account, PartsNavigation.PRO);
		fromMechanic("Alto oil filter");
		Enquiry enquiry = latest();
		assertThat(enquiry.status()).isEqualTo(Enquiry.QUOTED);

		clock.advance(Duration.ofHours(25));
		service.closeStaleQuotes();
		fromMechanic("1");

		assertThat(enquiries.get(account, enquiry.id()).status()).isEqualTo(Enquiry.CLOSED);
		assertThat(lastToMechanic().body()).startsWith("There is no open quote to book");
		assertThat(enquiries.list(account, List.of(Enquiry.NEW), null, 5)).isEmpty();
	}

	@Test
	void greetingsGetHelpAndBlockedMechanicsGetNothing() {
		fromMechanic("Namaste");
		assertThat(lastToMechanic().body()).contains("Send a photo of the old part");

		Mechanic raju = mechanics.byPhone(account, mechanicPhone).orElseThrow();
		mechanics.setBlocked(account, raju.id(), true);
		int before = outbox.whatsAppToPhone(account, mechanicPhone, 50).size();
		fromMechanic("Swift oil filter");
		assertThat(outbox.whatsAppToPhone(account, mechanicPhone, 50)).hasSize(before);
	}

	@Test
	void strangersOnTheSharedNumberAreTurnedAway() {
		String stranger = "+917" + String.format("%09d", n);
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(null, "wamid.test." + System.nanoTime(), stranger,
				"PLATFORM", null, "text", "Swift pad", null, null, null, null, null, OffsetDateTime.now(clock))).orElseThrow();
		processor.dispatch(id, stranger);

		assertThat(outbox.whatsAppToPhone(account, stranger, 5)).singleElement()
			.satisfies((m) -> assertThat(m.body()).contains("not set up with a spare parts shop"));
	}

	@Test
	void stockListImportsFromBillingSoftwareHeadingsAndExports() throws Exception {
		String csv = "Part No,Item Name,Type,Qty,Rate,Fits\r\nWB-22,Wiper blade 22 inch,wiper,20,290,Swift; i20\r\n"
				+ "XX-1,No price here,,3,,\r\nBP-SWF-PS,Front brake pad set,BRAKE_PAD,9,1175,Swift 2018-2024\r\n";

		mvc.perform(multipart("/parts/import")
			.file(new MockMultipartFile("file", "stock.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
			.with(user(owner))
			.with(csrf())).andExpect(flash().attribute("notice", "1 added, 1 updated; 1 lines skipped."));

		Part wiper = parts.byNumber(account, "WB-22").orElseThrow();
		assertThat(wiper.type()).isEqualTo(PartType.WIPER_BLADE);
		assertThat(wiper.stock()).isEqualTo(20);
		assertThat(parts.fitments(wiper.id())).hasSize(2);
		assertThat(parts.byNumber(account, "BP-SWF-PS").orElseThrow().price()).isEqualByComparingTo("1175");

		String exported = mvc.perform(get("/parts/export.csv").with(user(owner)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		assertThat(exported).startsWith("part_number,name,category,brand,price,mrp,stock,rack,fits,alt_numbers")
			.contains("CK-BOL,\"Clutch kit (plate, cover, bearing)\",CLUTCH_KIT");
	}

	@Test
	void setupKeepsAutomaticQuotesForPro() throws Exception {
		mvc.perform(post("/setup").with(user(owner)).with(csrf())
			.param("acknowledge", "true")
			.param("confidence", "90")
			.param("nudgeMinutes", "5")
			.param("readyMinutes", "20")).andExpect(status().is3xxRedirection());

		assertThat(settings.get(account).autoQuote()).as("off Pro the box is not sent; the choice is kept").isTrue();
		assertThat(settings.get(account).autoQuoteConfidence()).isEqualByComparingTo("0.90");
		assertThat(settings.get(account).readyMinutes()).isEqualTo(20);

		accounts.setPlan(account, PartsNavigation.PRO);
		mvc.perform(post("/setup").with(user(owner)).with(csrf()).param("acknowledge", "true"))
			.andExpect(status().is3xxRedirection());
		assertThat(settings.get(account).autoQuote()).isFalse();
	}

	@Test
	void everyPageRenders() throws Exception {
		accounts.setPlan(account, PartsNavigation.PRO);
		fromMechanic("Swift 2019 front brake pad");
		fromMechanic("1");
		fromMechanic("Bolero clutch plate");
		Enquiry waiting = latest();
		Part part = parts.byNumber(account, "CK-BOL").orElseThrow();
		long mechanicId = mechanics.byPhone(account, mechanicPhone).orElseThrow().id();

		for (String page : List.of("/", "/inbox", "/inbox/live", "/bookings", "/bookings?show=done", "/parts",
				"/parts?show=out&category=BRAKE_PAD&q=swift", "/parts/new", "/parts/" + part.id(), "/mechanics",
				"/mechanics?q=raju", "/mechanics/" + mechanicId, "/reports", "/reports?days=7", "/setup", "/settings",
				"/outbox", "/dev/whatsapp", "/enquiries/" + waiting.id(), "/enquiries/" + waiting.id() + "?q=clutch")) {
			mvc.perform(get(page).with(user(owner))).andExpect(status().isOk());
		}
	}

	// ---------------------------------------------------------------- helpers

	private void fromMechanic(String text) {
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(null, "wamid.test." + System.nanoTime(), mechanicPhone,
				"PLATFORM", "Raju", "text", text, null, null, null, null, null, OffsetDateTime.now(clock))).orElseThrow();
		processor.dispatch(id, mechanicPhone);
	}

	private void photoFromMechanic(String caption) {
		byte[] photo = PartSamples.box("ProStop", "Disc brake pad set", "Front axle", "PS-1123", "Swift", java.awt.Color.RED);
		long fileId = files.save(account, photo, "image/jpeg", "box.jpg").id();
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(null, "wamid.test." + System.nanoTime(), mechanicPhone,
				"PLATFORM", "Raju", "image", caption, null, "image/jpeg", "box.jpg", fileId, null, OffsetDateTime.now(clock)))
			.orElseThrow();
		processor.dispatch(id, mechanicPhone);
	}

	private void voiceFromMechanic() {
		byte[] ogg = "OggS\0\2fake-voice-note".getBytes(StandardCharsets.ISO_8859_1);
		long fileId = files.save(account, ogg, "audio/ogg", "voice.ogg").id();
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(null, "wamid.test." + System.nanoTime(), mechanicPhone,
				"PLATFORM", "Raju", "audio", null, null, "audio/ogg", "voice.ogg", fileId, null, OffsetDateTime.now(clock)))
			.orElseThrow();
		processor.dispatch(id, mechanicPhone);
	}

	private Enquiry latest() {
		long mechanicId = mechanics.byPhone(account, mechanicPhone).orElseThrow().id();
		long id = enquiries.list(account, List.of(Enquiry.NEW, Enquiry.QUOTED, Enquiry.BOOKED, Enquiry.NOT_AVAILABLE,
				Enquiry.CLOSED), mechanicId, 50)
			.stream()
			.max(java.util.Comparator.comparing(Enquiries.EnquiryRow::id))
			.orElseThrow()
			.id();
		return enquiries.get(account, id);
	}

	/** The latest WhatsApp message to the mechanic (the outbox lists newest first). */
	private OutboundMessage lastToMechanic() {
		List<OutboundMessage> messages = outbox.whatsAppToPhone(account, mechanicPhone, 50);
		assertThat(messages).isNotEmpty();
		return messages.getFirst();
	}

}
