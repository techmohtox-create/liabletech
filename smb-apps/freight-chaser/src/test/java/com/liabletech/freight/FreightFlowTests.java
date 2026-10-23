package com.liabletech.freight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.liabletech.freight.chase.ChaseService;
import com.liabletech.freight.chase.FollowUps;
import com.liabletech.freight.fleet.Fleet;
import com.liabletech.freight.invoice.Invoice;
import com.liabletech.freight.invoice.Invoices;
import com.liabletech.freight.party.Parties;
import com.liabletech.freight.party.PartyForm;
import com.liabletech.freight.pod.Pod;
import com.liabletech.freight.pod.PodReading;
import com.liabletech.freight.pod.Pods;
import com.liabletech.freight.trip.Trip;
import com.liabletech.freight.trip.TripForm;
import com.liabletech.freight.trip.Trips;
import com.liabletech.freight.whatsapp.PodSampleImage;
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
 * The whole money path: a driver's POD photo on WhatsApp, the invoice with proof, reminders as the
 * invoice ages, and the payment that stops them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FreightTestConfig.class)
@TestPropertySource(locations = "classpath:freight-test.properties")
class FreightFlowTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SignupService signups;

	@Autowired
	private Users users;

	@Autowired
	private Fleet fleet;

	@Autowired
	private Parties parties;

	@Autowired
	private Trips trips;

	@Autowired
	private Pods pods;

	@Autowired
	private Invoices invoices;

	@Autowired
	private ChaseService chase;

	@Autowired
	private FollowUps followUps;

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

	private long partyId;

	private long tripId;

	private String driverPhone;

	@BeforeEach
	void fleetWithATripOnTheRoad() {
		clock.set(FreightTestConfig.START);
		ai.reset();
		long n = System.nanoTime() % 1_000_000;
		String email = "owner" + n + "@example.com";
		account = signups.createDemo("Test Roadways", "Owner", "9" + String.format("%09d", n), email, "password123");
		owner = new CurrentUser(users.findByEmail(email).orElseThrow());
		long truck = fleet.addVehicle(account, "MH12AB1234", "32 ft");
		driverPhone = "+918" + String.format("%09d", n);
		fleet.addDriver(account, "Ramesh Yadav", driverPhone, truck);
		PartyForm party = new PartyForm();
		party.setName("Sharma Roadlines");
		party.setPhone("+917000000001");
		party.setEmail("accounts@sharma.example");
		party.setCreditDays(30);
		partyId = parties.create(account, party);
		TripForm trip = new TripForm();
		trip.setLrNumber("4521");
		trip.setLrDate(FreightTestConfig.START.minusDays(2));
		trip.setPartyId(partyId);
		trip.setVehicleId(truck);
		trip.setOrigin("Pune");
		trip.setDestination("Nagpur");
		trip.setFreightAmount(new BigDecimal("42000"));
		trip.setAdvanceAmount(new BigDecimal("10000"));
		tripId = trips.create(account, trip, Trip.IN_TRANSIT);
	}

	@Test
	void clearPodPhotoCompletesTheTripAndThanksTheDriver() {
		ai.willRead(PodReading.class, reading("LR-4521", true, 0.94));

		sendPhotoFromDriver("LR 4521");

		Trip trip = trips.get(account, tripId);
		assertThat(trip.status()).isEqualTo(Trip.DELIVERED);
		assertThat(trip.deliveredOn()).isEqualTo(FreightTestConfig.START);
		Pod pod = pods.ofTrip(account, tripId).getFirst();
		assertThat(pod.reviewStatus()).isEqualTo(Pod.CONFIRMED);
		assertThat(pod.lrNumber()).isEqualTo("LR-4521");
		assertThat(lastWhatsAppTo(driverPhone).body()).contains("POD received for LR 4521").contains("Pune → Nagpur");
	}

	@Test
	void podWithoutStampWaitsForTheOwnerAndAsksForAStampedCopy() {
		ai.willRead(PodReading.class, reading("4521", false, 0.9));

		sendPhotoFromDriver("");

		assertThat(trips.get(account, tripId).status()).isEqualTo(Trip.IN_TRANSIT);
		assertThat(pods.list(account, Pod.NEEDS_REVIEW, 10)).hasSize(1);
		assertThat(lastWhatsAppTo(driverPhone).body()).contains("cannot see the receiver's stamp or signature");
	}

	@Test
	void blurredPhotoIsSentBack() {
		ai.willRead(PodReading.class, reading("45?1", true, 0.3));

		sendPhotoFromDriver("");

		assertThat(lastWhatsAppTo(driverPhone).body()).contains("could not read this photo clearly");
	}

	@Test
	void withoutAiTheCaptionStillMatchesTheTrip() {
		ai.willFail(PodReading.class, com.liabletech.platform.ai.AiResult.Outcome.DISABLED);

		sendPhotoFromDriver("lr no 4521");

		Pod pod = pods.list(account, null, 10).stream().map((row) -> pods.get(account, row.id())).findFirst().orElseThrow();
		assertThat(pod.readingStatus()).isEqualTo(Pod.MANUAL);
		assertThat(pod.tripId()).isEqualTo(tripId);
		assertThat(pod.reviewStatus()).isEqualTo(Pod.NEEDS_REVIEW);
	}

	@Test
	void invoiceGoesOutWithThePodAndIsChasedUntilPaid() throws Exception {
		ai.willRead(PodReading.class, reading("4521", true, 0.95));
		sendPhotoFromDriver("LR 4521");

		mvc.perform(post("/invoices").with(user(owner)).with(csrf())
			.param("tripIds", String.valueOf(tripId))
			.param("partyId", String.valueOf(partyId))
			.param("invoiceDate", FreightTestConfig.START.toString())
			.param("gstMode", "NONE")
			.param("gstRate", "5")
			.param("sendNow", "true")).andExpect(status().is3xxRedirection());

		Invoice invoice = invoices.open(account).getFirst();
		assertThat(invoice.number()).isEqualTo("FC/2026-27/0001");
		assertThat(invoice.total()).isEqualByComparingTo("32000");
		assertThat(invoice.dueDate()).isEqualTo(FreightTestConfig.START.plusDays(30));
		assertThat(invoice.status()).isEqualTo(Invoice.SENT);
		assertThat(trips.get(account, tripId).status()).isEqualTo(Trip.INVOICED);
		List<OutboundMessage> sent = outbox.about(account, "invoice", invoice.id());
		assertThat(sent).extracting(OutboundMessage::channel).containsExactlyInAnyOrder("WHATSAPP", "EMAIL");
		long pdfId = sent.getFirst().attachmentIds().getFirst();
		try (PdfReader pdf = new PdfReader(files.read(pdfId))) {
			assertThat(pdf.getNumberOfPages()).as("invoice page plus the POD photo").isEqualTo(2);
		}
		// First reminder: two days before the due date.
		assertThat(invoice.nextFollowUpOn()).isEqualTo(invoice.dueDate().minusDays(2));

		clock.set(invoice.dueDate().plusDays(5));
		assertThat(chase.runFor(account)).isEqualTo(1);
		Invoice afterReminder = invoices.get(account, invoice.id());
		assertThat(afterReminder.followUpCount()).isEqualTo(1);
		assertThat(afterReminder.nextFollowUpOn()).isEqualTo(invoice.dueDate().plusDays(7));
		OutboundMessage reminder = outbox.about(account, "invoice", invoice.id()).getFirst();
		assertThat(reminder.body()).contains("5 days overdue").contains("₹32,000");
		assertThat(reminder.attachmentIds()).hasSize(1);
		assertThat(chase.runFor(account)).as("nothing more due today").isZero();

		mvc.perform(post("/invoices/" + invoice.id() + "/payments").with(user(owner)).with(csrf())
			.param("receivedOn", invoice.dueDate().plusDays(6).toString())
			.param("amount", "31680")
			.param("tds", "320")
			.param("mode", "NEFT")
			.param("thankYou", "true")).andExpect(status().is3xxRedirection());

		Invoice paid = invoices.get(account, invoice.id());
		assertThat(paid.status()).isEqualTo(Invoice.PAID);
		assertThat(trips.get(account, tripId).status()).isEqualTo(Trip.PAID);
		clock.set(invoice.dueDate().plusDays(30));
		assertThat(chase.runFor(account)).isZero();
		assertThat(followUps.forInvoice(account, invoice.id())).extracting("kind").contains("SENT", "REMINDER", "PAYMENT");
	}

	@Test
	void promiseHoldsRemindersUntilTheDayAfter() throws Exception {
		ai.willRead(PodReading.class, reading("4521", true, 0.95));
		sendPhotoFromDriver("LR 4521");
		mvc.perform(post("/invoices").with(user(owner)).with(csrf())
			.param("tripIds", String.valueOf(tripId))
			.param("partyId", String.valueOf(partyId))
			.param("invoiceDate", FreightTestConfig.START.toString())
			.param("gstMode", "NONE")
			.param("sendNow", "true"));
		Invoice invoice = invoices.open(account).getFirst();

		clock.set(invoice.dueDate().plusDays(3));
		mvc.perform(post("/invoices/" + invoice.id() + "/promise").with(user(owner)).with(csrf())
			.param("promisedOn", invoice.dueDate().plusDays(10).toString())
			.param("note", "Vijay ji on phone")).andExpect(status().is3xxRedirection());

		clock.set(invoice.dueDate().plusDays(10));
		assertThat(chase.runFor(account)).isZero();
		clock.set(invoice.dueDate().plusDays(11));
		assertThat(chase.runFor(account)).isEqualTo(1);
	}

	@Test
	void unknownNumberIsToldToGetRegistered() {
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(null, "wamid.unknown." + System.nanoTime(),
				"+916000000009", "PLATFORM", null, "text", "hello", null, null, null, null, null,
				OffsetDateTime.now(clock))).orElseThrow();
		processor.dispatch(id, "+916000000009");

		assertThat(outbox.whatsAppToPhone(account, "+916000000009", 5)).singleElement()
			.satisfies((m) -> assertThat(m.body()).contains("not registered as a driver"));
	}

	@Test
	void everyPageRenders() throws Exception {
		ai.willRead(PodReading.class, reading("4521", false, 0.9));
		sendPhotoFromDriver("LR 4521");
		long podId = pods.list(account, null, 1).getFirst().id();
		for (String page : List.of("/", "/pods", "/pods?show=all", "/pods/" + podId, "/trips", "/trips/" + tripId,
				"/trips/new", "/trips/" + tripId + "/edit", "/invoices", "/invoices?show=all", "/parties", "/parties/new",
				"/parties/" + partyId + "/edit", "/fleet", "/chasing", "/settings", "/outbox", "/dev/whatsapp")) {
			mvc.perform(get(page).with(user(owner))).andExpect(status().isOk());
		}
	}

	private void sendPhotoFromDriver(String caption) {
		byte[] photo = PodSampleImage.render("Test Roadways", "4521", FreightTestConfig.START.minusDays(2),
				FreightTestConfig.START, "MH12AB1234", "Pune", "Nagpur", "Deccan Agro", "Vidarbha Traders", "Rice", 240,
				"Recd OK");
		long fileId = files.save(account, photo, "image/jpeg", "pod.jpg").id();
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(null, "wamid.test." + System.nanoTime(), driverPhone,
				"PLATFORM", "Ramesh", "image", caption, null, "image/jpeg", "pod.jpg", fileId, null,
				OffsetDateTime.now(clock))).orElseThrow();
		processor.dispatch(id, driverPhone);
	}

	private OutboundMessage lastWhatsAppTo(String phone) {
		List<OutboundMessage> messages = outbox.whatsAppTo(account, phone, 10);
		assertThat(messages).isNotEmpty();
		return messages.getLast();
	}

	private static PodReading reading(String lr, boolean stamped, double confidence) {
		return new PodReading(PodReading.DocumentType.POD, lr, "2026-09-29", "2026-10-01", "Deccan Agro",
				"Vidarbha Traders", "Pune", "Nagpur", "MH12AB1234", "240 bags", "", stamped, stamped, "Recd OK",
				confidence);
	}

}
