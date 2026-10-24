package com.liabletech.amc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.liabletech.amc.contract.Contract;
import com.liabletech.amc.contract.ContractService;
import com.liabletech.amc.contract.Contracts;
import com.liabletech.amc.customer.Appliance;
import com.liabletech.amc.customer.ApplianceKind;
import com.liabletech.amc.customer.Appliances;
import com.liabletech.amc.customer.Customer;
import com.liabletech.amc.customer.Customers;
import com.liabletech.amc.label.LabelReading;
import com.liabletech.amc.label.LabelScan;
import com.liabletech.amc.label.LabelScans;
import com.liabletech.amc.plan.AmcPlan;
import com.liabletech.amc.plan.Plans;
import com.liabletech.amc.reminder.ReminderService;
import com.liabletech.amc.reminder.ReminderSettingsRepository;
import com.liabletech.amc.reminder.Reminders;
import com.liabletech.amc.request.ServiceRequest;
import com.liabletech.amc.request.ServiceRequests;
import com.liabletech.amc.team.Technicians;
import com.liabletech.amc.whatsapp.LabelSamples;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.account.ProfileForm;
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
 * From a technician's label photo to a contract, the reminders before it ends, the customer's
 * answer on WhatsApp or the link, and the office confirming the renewal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AmcTestConfig.class)
@TestPropertySource(locations = "classpath:amc-test.properties")
class AmcFlowTests {

	private static final LocalDate TODAY = AmcTestConfig.TODAY;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SignupService signups;

	@Autowired
	private Users users;

	@Autowired
	private Accounts accounts;

	@Autowired
	private Technicians technicians;

	@Autowired
	private Customers customers;

	@Autowired
	private Appliances appliances;

	@Autowired
	private Plans plans;

	@Autowired
	private Contracts contracts;

	@Autowired
	private ContractService contractService;

	@Autowired
	private ServiceRequests requests;

	@Autowired
	private Reminders reminders;

	@Autowired
	private ReminderService reminderService;

	@Autowired
	private ReminderSettingsRepository settings;

	@Autowired
	private LabelScans scans;

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

	private String technicianPhone;

	private long n;

	@BeforeEach
	void serviceBusinessWithATechnician() {
		clock.set(TODAY);
		ai.reset();
		n = System.nanoTime() % 1_000_000;
		String email = "service" + n + "@example.com";
		ownerPhone = "+919" + String.format("%09d", n);
		technicianPhone = "+918" + String.format("%09d", n);
		account = signups.createDemo("Test Cooling Services", "Owner", ownerPhone, email, "password123");
		owner = new CurrentUser(users.findByEmail(email).orElseThrow());
		technicians.add(account, "Ravi", technicianPhone);
	}

	@Test
	void newBusinessGetsStarterPlansAndTheStandardSchedule() {
		assertThat(plans.all(account)).extracting(AmcPlan::name)
			.containsExactly("AC Care AMC", "AC Comprehensive AMC", "RO Care AMC", "RO Gold AMC");
		assertThat(settings.get(account).offsets()).containsExactly(-30, -15, -7, 0, 7, 30);
	}

	@Test
	void clearLabelWithTheCustomersNumberBecomesAContractStraightAway() {
		String customerPhone = customerPhone(1);
		ai.willRead(LabelReading.class, splitAcReading("4B1206" + n, 0.93));

		photoFromTechnician(customerPhone.substring(3) + " Sharma AMC 1499 paid bedroom");

		Customer customer = customers.byPhone(account, customerPhone).orElseThrow();
		assertThat(customer.name()).isEqualTo("Sharma");
		Appliance appliance = appliances.forCustomer(customer.id()).getFirst();
		assertThat(appliance.applianceKind()).isEqualTo(ApplianceKind.SPLIT_AC);
		assertThat(appliance.serialNumber()).isEqualTo("4B1206" + n);
		assertThat(appliance.location()).isEqualTo("Bedroom");
		Contract contract = contracts.forCustomer(customer.id()).getFirst();
		assertThat(contract.planName()).isEqualTo("AC Care AMC");
		assertThat(contract.price()).isEqualByComparingTo("1499");
		assertThat(contract.startsOn()).isEqualTo(TODAY);
		assertThat(contract.endsOn()).isEqualTo(TODAY.plusYears(1).minusDays(1));
		assertThat(contract.paid()).isTrue();
		assertThat(contract.source()).isEqualTo("TECHNICIAN");
		assertThat(lastTo(technicianPhone).body()).startsWith("✅ Saved: Voltas 1.5 TR split AC (Bedroom), SN 4B1206" + n)
			.contains("AMC: AC Care AMC, ₹1,499, until 30 Sep 2027, paid.");
	}

	@Test
	void unclearLabelWaitsForTheOfficeAndIsSavedOnce() throws Exception {
		ai.willRead(LabelReading.class, splitAcReading("E00" + n, 0.55));

		photoFromTechnician("AMC 1499 hall");

		assertThat(lastTo(technicianPhone).body()).contains("Which customer is it?");
		LabelScan scan = scans.get(account, scans.list(account, LabelScan.NEEDS_REVIEW, 5).getFirst().id());
		assertThat(scan.amcSold()).isTrue();
		mvc.perform(get("/labels/" + scan.id()).with(user(owner))).andExpect(status().isOk());

		String customerPhone = customerPhone(2);
		long planId = plan("AC Care AMC").id();
		mvc.perform(post("/labels/" + scan.id()).with(user(owner)).with(csrf())
			.param("kind", "SPLIT_AC")
			.param("brand", "Voltas")
			.param("model", "183V CZTT")
			.param("serialNumber", "E00" + n)
			.param("customerPhone", customerPhone.substring(3))
			.param("customerName", "Meera Joshi")
			.param("planId", String.valueOf(planId))
			.param("paymentMode", "CASH")).andExpect(status().is3xxRedirection());

		Customer customer = customers.byPhone(account, customerPhone).orElseThrow();
		assertThat(contracts.forCustomer(customer.id())).singleElement()
			.satisfies((c) -> assertThat(c.paid()).isFalse());
		assertThat(scans.get(account, scan.id()).status()).isEqualTo(LabelScan.LINKED);

		mvc.perform(post("/labels/" + scan.id()).with(user(owner)).with(csrf())
			.param("kind", "SPLIT_AC")
			.param("brand", "Voltas")
			.param("model", "183V CZTT X")
			.param("serialNumber", "E00" + n)
			.param("customerPhone", customerPhone.substring(3))
			.param("planId", String.valueOf(planId))).andExpect(status().is3xxRedirection());

		assertThat(contracts.forCustomer(customer.id())).as("saving again corrects, it does not sell twice").hasSize(1);
		assertThat(appliances.forCustomer(customer.id())).singleElement()
			.satisfies((a) -> assertThat(a.model()).isEqualTo("183V CZTT X"));
	}

	@Test
	void labelTheAiCannotReadIsTypedInByTheOffice() {
		ai.willFail(LabelReading.class, AiResult.Outcome.FAILED);

		photoFromTechnician(customerPhone(3).substring(3) + " no amc");

		assertThat(lastTo(technicianPhone).body()).contains("could not be read automatically");
		assertThat(scans.list(account, LabelScan.NEEDS_REVIEW, 5)).singleElement()
			.satisfies((s) -> assertThat(s.readingStatus()).isEqualTo(LabelScan.MANUAL));
	}

	@Test
	void sameApplianceSoldAgainRenewsItsContract() {
		String customerPhone = customerPhone(4);
		ai.willRead(LabelReading.class, splitAcReading("SN" + n, 0.95));
		photoFromTechnician(customerPhone.substring(3) + " Patil AMC 1499");
		clock.set(TODAY.plusDays(350));

		photoFromTechnician(customerPhone.substring(3) + " Patil comprehensive AMC 3999 paid");

		Customer customer = customers.byPhone(account, customerPhone).orElseThrow();
		List<Contract> all = contracts.forCustomer(customer.id());
		assertThat(all).hasSize(2);
		Contract renewed = all.getFirst();
		assertThat(renewed.planName()).isEqualTo("AC Comprehensive AMC");
		assertThat(renewed.startsOn()).as("the new year follows the old one").isEqualTo(TODAY.plusYears(1));
		assertThat(all.getLast().status()).isEqualTo(Contract.RENEWED);
		assertThat(appliances.forCustomer(customer.id())).hasSize(1);
	}

	@Test
	void remindersGoOutOnScheduleOnceEach() {
		Contract contract = contractEnding(TODAY.plusDays(30), 5);

		assertThat(reminderService.runFor(account)).isEqualTo(1);
		assertThat(reminderService.runFor(account)).as("not twice the same day").isZero();
		OutboundMessage first = lastTo(contract);
		assertThat(first.templateName()).isEqualTo("amc_renewal_reminder");
		assertThat(first.body()).contains("ends on 31 Oct 2026 (30 days left)")
			.contains("/p/renew/" + contract.renewalToken());

		clock.set(TODAY.plusDays(15));
		assertThat(reminderService.runFor(account)).isEqualTo(1);
		clock.set(TODAY.plusDays(30));
		assertThat(reminderService.runFor(account)).as("the 7-day one was missed by more than the grace days")
			.isEqualTo(1);
		assertThat(lastTo(contract).body()).contains("ends today");

		assertThat(reminders.forContract(contract.id())).extracting(Reminders.SentReminder::offsetDays)
			.containsExactly(-30, -15, 0);
	}

	@Test
	void customerRepliesYesAndTheOfficeConfirmsTheRenewal() throws Exception {
		Contract contract = contractEnding(TODAY.plusDays(10), 6);
		Customer customer = customers.get(account, contract.customerId());

		fromCustomer(customer.phone(), "Yes");

		ServiceRequest request = requests.openFor(contract.id(), ServiceRequest.RENEWAL).orElseThrow();
		assertThat(request.source()).isEqualTo("WHATSAPP");
		assertThat(lastTo(customer.phone()).body()).contains("noted your renewal of AC Care AMC").contains("₹1,499");
		assertThat(lastTo(ownerPhone).templateName()).isEqualTo("amc_new_request");
		assertThat(lastTo(ownerPhone).body()).contains("wants to renew AC Care AMC at ₹1,499");

		fromCustomer(customer.phone(), "yes");
		assertThat(requests.list(account, List.of(ServiceRequest.NEW), 50))
			.filteredOn((r) -> r.contractId().equals(contract.id()))
			.as("asking twice is one request")
			.hasSize(1);

		mvc.perform(post("/requests/" + request.id() + "/renew").with(user(owner)).with(csrf())
			.param("planId", String.valueOf(plan("AC Care AMC").id()))
			.param("paid", "true")
			.param("paymentMode", "UPI")).andExpect(status().is3xxRedirection());

		Contract old = contracts.get(account, contract.id());
		assertThat(old.status()).isEqualTo(Contract.RENEWED);
		Contract renewed = contracts.get(account, old.renewedById());
		assertThat(renewed.startsOn()).isEqualTo(contract.endsOn().plusDays(1));
		assertThat(renewed.paidAmount()).isEqualByComparingTo("1499");
		assertThat(renewed.paymentMode()).isEqualTo("UPI");
		assertThat(requests.get(account, request.id()).status()).isEqualTo(ServiceRequest.DONE);
		assertThat(lastTo(customer.phone()).body()).contains("thank you! Your AC Care AMC")
			.contains("renewed until " + renewed.endsOn().getDayOfMonth());

		clock.set(TODAY.plusDays(10));
		assertThat(reminderService.runFor(account)).as("a renewed contract gets no more reminders").isZero();
	}

	@Test
	void stopEndsRemindersAndStartBringsThemBack() {
		Contract contract = contractEnding(TODAY.plusDays(30), 7);
		Customer customer = customers.get(account, contract.customerId());

		fromCustomer(customer.phone(), "STOP");

		assertThat(customers.get(account, customer.id()).optedOut()).isTrue();
		assertThat(lastTo(customer.phone()).body()).contains("You will not get reminders");
		assertThat(reminderService.runFor(account)).isZero();

		fromCustomer(customer.phone(), "start");
		assertThat(customers.get(account, customer.id()).optedOut()).isFalse();
		assertThat(reminderService.runFor(account)).isEqualTo(1);
	}

	@Test
	void otherMessagesGoToTheOwnerAndStrangersBecomeEnquiries() {
		Contract contract = contractEnding(TODAY.plusDays(90), 8);
		Customer customer = customers.get(account, contract.customerId());

		fromCustomer(customer.phone(), "Water is dripping from the indoor unit");

		assertThat(lastTo(ownerPhone).templateName()).isEqualTo("amc_customer_message");
		assertThat(lastTo(ownerPhone).body()).contains("Water is dripping");
		assertThat(lastTo(customer.phone()).body()).contains("Reply YES to renew your AMC, or SERVICE to book a visit");

		String stranger = customerPhone(9);
		fromCustomer(stranger, "Need AC service in Baner");
		assertThat(lastTo(stranger).body()).as("on the shared number nobody knows whose customer this is")
			.contains("not registered with any service business");

		toBusinessNumber(stranger, "Need AC service in Baner");

		Customer created = customers.byPhone(account, stranger).orElseThrow();
		assertThat(requests.forCustomer(account, created.id())).singleElement().satisfies((r) -> {
			assertThat(r.kind()).isEqualTo(ServiceRequest.REPAIR);
			assertThat(r.source()).isEqualTo("WHATSAPP");
			assertThat(r.note()).isEqualTo("Need AC service in Baner");
		});
		assertThat(lastTo(ownerPhone).body()).startsWith("🔔 New enquiry from").contains("Need AC service in Baner");
		assertThat(lastTo(stranger).body()).contains("we will call you back");
	}

	@Test
	void renewalLinkLetsTheCustomerRenewPayAndBookAVisit() throws Exception {
		ProfileForm profile = ProfileForm.of(accounts.get(account));
		profile.setUpiId("testcooling@okaxis");
		accounts.updateProfile(account, profile);
		Contract contract = contractEnding(TODAY.plusDays(20), 10);
		String page = "/p/renew/" + contract.renewalToken();

		String html = mvc.perform(get(page)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(html).contains("Renew for another year").contains("Book a service visit").contains("AC Comprehensive AMC");

		mvc.perform(post(page + "/renew").with(csrf()).param("planId", String.valueOf(plan("AC Comprehensive AMC").id())))
			.andExpect(redirectedUrl(page + "?done=renewal"));

		ServiceRequest renewal = requests.openFor(contract.id(), ServiceRequest.RENEWAL).orElseThrow();
		assertThat(renewal.source()).isEqualTo("LINK");
		assertThat(renewal.planId()).isEqualTo(plan("AC Comprehensive AMC").id());
		html = mvc.perform(get(page + "?done=renewal")).andReturn().getResponse().getContentAsString();
		assertThat(html).contains("Renewal requested")
			.contains("upi://pay?pa=testcooling@okaxis&amp;pn=Test%20Cooling%20Services&amp;am=3999.00&amp;cu=INR");

		mvc.perform(post(page + "/service").with(csrf())
			.param("preferredOn", TODAY.plusDays(2).toString())
			.param("slot", "Afternoon (12–4 pm)")
			.param("note", "Noise from outdoor unit")).andExpect(redirectedUrl(page + "?done=service"));

		ServiceRequest visit = requests.openFor(contract.id(), ServiceRequest.SERVICE).orElseThrow();
		assertThat(visit.preferredOn()).isEqualTo(TODAY.plusDays(2));
		assertThat(visit.preferredSlot()).isEqualTo("Afternoon");
		assertThat(visit.note()).isEqualTo("Noise from outdoor unit");

		mvc.perform(post(page + "/stop").with(csrf())).andExpect(status().is3xxRedirection());
		assertThat(customers.get(account, contract.customerId()).optedOut()).isTrue();

		mvc.perform(get("/p/renew/not-a-real-token-at-all")).andExpect(status().isNotFound());
	}

	@Test
	void oldLinkLeadsToTheRenewedContract() throws Exception {
		Contract contract = contractEnding(TODAY.plusDays(5), 11);
		long renewedId = contractService.renew(account, contract.id(), null, null, false, null);
		Contract renewed = contracts.get(account, renewedId);

		mvc.perform(get("/p/renew/" + contract.renewalToken()))
			.andExpect(redirectedUrl("/p/renew/" + renewed.renewalToken()));
	}

	@Test
	void bookingAVisitTellsTheCustomerAndCountsWhenDone() throws Exception {
		Contract contract = contractEnding(TODAY.plusDays(200), 12);
		Customer customer = customers.get(account, contract.customerId());
		fromCustomer(customer.phone(), "service");
		ServiceRequest request = requests.openFor(contract.id(), ServiceRequest.SERVICE).orElseThrow();
		long technician = technicians.all(account).getFirst().id();

		mvc.perform(post("/requests/" + request.id() + "/schedule").with(user(owner)).with(csrf())
			.param("on", TODAY.plusDays(3).toString())
			.param("technicianId", String.valueOf(technician))).andExpect(status().is3xxRedirection());

		assertThat(requests.get(account, request.id()).status()).isEqualTo(ServiceRequest.SCHEDULED);
		assertThat(lastTo(customer.phone()).body()).contains("your service visit is booked for 4 Oct 2026")
			.contains("Technician: Ravi");

		mvc.perform(post("/requests/" + request.id() + "/done").with(user(owner)).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertThat(requests.visitsDone(contract.id())).isEqualTo(1);
	}

	@Test
	void officeCanRemindNowAndCancel() throws Exception {
		Contract contract = contractEnding(TODAY.plusDays(100), 13);

		mvc.perform(post("/contracts/" + contract.id() + "/remind").with(user(owner)).with(csrf()))
			.andExpect(flash().attribute("notice", "Reminder sent on WhatsApp."));
		assertThat(lastTo(contract).body()).contains("(100 days left)");

		mvc.perform(post("/contracts/" + contract.id() + "/cancel").with(user(owner)).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertThat(contracts.get(account, contract.id()).status()).isEqualTo(Contract.CANCELLED);
		mvc.perform(post("/contracts/" + contract.id() + "/remind").with(user(owner)).with(csrf()))
			.andExpect(flash().attribute("error", "This contract is already cancelled."));
	}

	@Test
	void setupChangesPlansTechniciansAndTheSchedule() throws Exception {
		mvc.perform(post("/setup/plans").with(user(owner)).with(csrf())
			.param("name", "Geyser Care AMC")
			.param("family", "WATER_HEATER")
			.param("price", "999")
			.param("months", "12")
			.param("visits", "1")).andExpect(flash().attribute("notice", "Plan added."));
		AmcPlan geyser = plan("Geyser Care AMC");
		assertThat(geyser.family()).isEqualTo("WATER_HEATER");

		Contract old = contractEnding(TODAY.plusDays(30), 14);
		AmcPlan care = plan("AC Care AMC");
		mvc.perform(post("/setup/plans/" + care.id()).with(user(owner)).with(csrf())
			.param("name", care.name())
			.param("price", "1599")
			.param("months", "12")
			.param("visits", "3")
			.param("active", "true")).andExpect(status().is3xxRedirection());
		assertThat(old.price()).isEqualByComparingTo("1499");
		assertThat(reminderService.renewalPrice(account, old)).as("reminders quote today's price")
			.isEqualByComparingTo("1599");

		mvc.perform(post("/setup/reminders").with(user(owner)).with(csrf())
			.param("renewalOffsets", "-20 0 10")
			.param("serviceReminders", "true")).andExpect(status().is3xxRedirection());
		assertThat(settings.get(account).offsets()).containsExactly(-20, 0, 10);
		assertThat(settings.get(account).autoSend()).isFalse();

		mvc.perform(post("/setup/technicians").with(user(owner)).with(csrf())
			.param("name", "Asif")
			.param("phone", technicianPhone.substring(3))).andExpect(flash().attribute("error",
					"That number is already registered as a technician."));

		mvc.perform(post("/setup/reminders/run").with(user(owner)).with(csrf()))
			.andExpect(flash().attribute("notice", "Nothing was due today; every reminder has already gone out."));
	}

	@Test
	void everyPageRenders() throws Exception {
		Contract contract = contractEnding(TODAY.plusDays(12), 15);
		reminderService.runFor(account);
		fromCustomer(customers.get(account, contract.customerId()).phone(), "yes");
		ai.willRead(LabelReading.class, splitAcReading("PG" + n, 0.4));
		photoFromTechnician("AMC 1499");
		long scanId = scans.list(account, LabelScan.NEEDS_REVIEW, 5).getFirst().id();

		for (String page : List.of("/", "/contracts", "/contracts?show=ALL", "/contracts?show=LAPSED&q=customer",
				"/contracts/" + contract.id(), "/requests", "/requests?show=done", "/customers", "/customers?q=9",
				"/customers/" + contract.customerId(), "/labels", "/labels?show=all", "/labels/" + scanId, "/setup",
				"/settings", "/outbox", "/dev/whatsapp")) {
			mvc.perform(get(page).with(user(owner))).andExpect(status().isOk());
		}
		mvc.perform(get("/p/renew/" + contract.renewalToken())).andExpect(status().isOk());
	}

	// ---------------------------------------------------------------- helpers

	private String customerPhone(int i) {
		return "+917" + String.format("%07d", n % 10_000_000).substring(0, 7) + String.format("%02d", i);
	}

	private Contract contractEnding(LocalDate endsOn, int i) {
		Customer customer = customers.findOrCreate(account, customerPhone(i), "Customer " + i, OffsetDateTime.now(clock));
		long applianceId = appliances.create(account, customer.id(), ApplianceKind.SPLIT_AC, "LG", "PS-Q19", "SN" + n + i,
				"1.5 TR", "R32", null, "bedroom", null, OffsetDateTime.now(clock));
		AmcPlan care = plan("AC Care AMC");
		long id = contractService.sell(account, customer.id(), applianceId, care, care.price(),
				endsOn.minusYears(1).plusDays(1), "OFFICE", null, null);
		return contracts.get(account, id);
	}

	private AmcPlan plan(String name) {
		return plans.all(account).stream().filter((p) -> p.name().equals(name)).findFirst().orElseThrow();
	}

	private static LabelReading splitAcReading(String serial, double confidence) {
		return new LabelReading("SPLIT_AC", "Voltas", "183V CZTT", serial, "OUTDOOR", "1.5 TR", "R32", "02/2023",
				"5 STAR", confidence);
	}

	private void photoFromTechnician(String caption) {
		byte[] photo = LabelSamples.splitAc("Voltas", "183V CZTT", "SN" + n, "1.5 TR", "R32", "02/2023");
		long fileId = files.save(account, photo, "image/jpeg", "label.jpg").id();
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(null, "wamid.test." + System.nanoTime(),
				technicianPhone, "PLATFORM", "Ravi", "image", caption, null, "image/jpeg", "label.jpg", fileId, null,
				OffsetDateTime.now(clock))).orElseThrow();
		processor.dispatch(id, technicianPhone);
	}

	private void fromCustomer(String phone, String text) {
		toNumber(null, phone, text);
	}

	/** A message to the business's own WhatsApp number, which tells the app whose customer it is. */
	private void toBusinessNumber(String phone, String text) {
		toNumber(account, phone, text);
	}

	private void toNumber(Long accountId, String phone, String text) {
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(accountId, "wamid.test." + System.nanoTime(), phone,
				"PLATFORM", null, "text", text, null, null, null, null, null, OffsetDateTime.now(clock))).orElseThrow();
		processor.dispatch(id, phone);
	}

	/** The latest WhatsApp message to a number (the outbox lists newest first). */
	private OutboundMessage lastTo(String phone) {
		List<OutboundMessage> messages = outbox.whatsAppToPhone(account, phone, 50);
		assertThat(messages).isNotEmpty();
		return messages.getFirst();
	}

	private OutboundMessage lastTo(Contract contract) {
		return lastTo(customers.get(account, contract.customerId()).phone());
	}

}
