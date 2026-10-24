package com.liabletech.amc.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.liabletech.amc.contract.Contract;
import com.liabletech.amc.contract.Contracts;
import com.liabletech.amc.customer.Appliance;
import com.liabletech.amc.customer.ApplianceKind;
import com.liabletech.amc.customer.Appliances;
import com.liabletech.amc.customer.Customer;
import com.liabletech.amc.customer.Customers;
import com.liabletech.amc.label.CaptionParser;
import com.liabletech.amc.label.LabelScan;
import com.liabletech.amc.label.LabelScans;
import com.liabletech.amc.label.LabelService;
import com.liabletech.amc.plan.AmcPlan;
import com.liabletech.amc.plan.Plans;
import com.liabletech.amc.reminder.ReminderMessages;
import com.liabletech.amc.reminder.ReminderService;
import com.liabletech.amc.reminder.ReminderSettings;
import com.liabletech.amc.reminder.Reminders;
import com.liabletech.amc.request.RequestService;
import com.liabletech.amc.request.ServiceRequest;
import com.liabletech.amc.request.ServiceRequests;
import com.liabletech.amc.team.Technicians;
import com.liabletech.amc.whatsapp.AmcWhatsApp;
import com.liabletech.amc.whatsapp.LabelSamples;
import com.liabletech.platform.AppProperties;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.account.ProfileForm;
import com.liabletech.platform.account.SignupService;
import com.liabletech.platform.account.Users;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.messaging.Messenger;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.messaging.Outgoing;
import com.liabletech.platform.text.IndianMoney;
import com.liabletech.platform.whatsapp.InboundMessage;
import com.liabletech.platform.whatsapp.InboundMessages;
import com.liabletech.platform.whatsapp.SimulatorController;

/**
 * A fictional AC and RO service business in Pune with a year of contracts: six ending this month
 * (two customers already asked to renew), four lapsed, renewals made in the last weeks, visits
 * booked for the coming days and two label photos waiting for the office. Reminders, replies and
 * renewals carry the dates they would have had.
 */
@Component
public class AmcDemoData implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AmcDemoData.class);

	private static final List<Integer> OFFSETS = ReminderSettings.parse(ReminderSettings.DEFAULT_OFFSETS);

	private final AppProperties properties;

	private final Users users;

	private final SignupService signups;

	private final Accounts accounts;

	private final Technicians technicians;

	private final Customers customers;

	private final Appliances appliances;

	private final Plans plans;

	private final Contracts contracts;

	private final ServiceRequests requests;

	private final RequestService requestService;

	private final Reminders reminders;

	private final ReminderService reminderService;

	private final LabelScans scans;

	private final FileStore files;

	private final Messenger messenger;

	private final InboundMessages inbound;

	private final AmcWhatsApp whatsApp;

	private final JdbcClient jdbc;

	private final Clock clock;

	private long account;

	private Account business;

	private LocalDate today;

	private OffsetDateTime now;

	private Map<String, AmcPlan> plan;

	private long sunil;

	private long imran;

	public AmcDemoData(AppProperties properties, Users users, SignupService signups, Accounts accounts,
			Technicians technicians, Customers customers, Appliances appliances, Plans plans, Contracts contracts,
			ServiceRequests requests, RequestService requestService, Reminders reminders, ReminderService reminderService,
			LabelScans scans, FileStore files, Messenger messenger, InboundMessages inbound, AmcWhatsApp whatsApp,
			JdbcClient jdbc, Clock clock) {
		this.properties = properties;
		this.users = users;
		this.signups = signups;
		this.accounts = accounts;
		this.technicians = technicians;
		this.customers = customers;
		this.appliances = appliances;
		this.plans = plans;
		this.contracts = contracts;
		this.requests = requests;
		this.requestService = requestService;
		this.reminders = reminders;
		this.reminderService = reminderService;
		this.scans = scans;
		this.files = files;
		this.messenger = messenger;
		this.inbound = inbound;
		this.whatsApp = whatsApp;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		AppProperties.Demo demo = properties.demo();
		if (!demo.enabled() || users.findByEmail(demo.email()).isPresent()) {
			return;
		}
		today = LocalDate.now(clock);
		now = OffsetDateTime.now(clock);
		account = signups.createDemo("CoolCare AC & RO Services", "Prakash Jadhav", "+919000070000", demo.email(),
				demo.password());
		ProfileForm profile = ProfileForm.of(accounts.get(account));
		profile.setAddress("Shop 4, Sai Plaza, Karve Road, Kothrud, Pune 411038");
		// Not a real UPI ID: "example" is no bank's handle, so a test payment cannot reach anyone.
		profile.setUpiId("coolcare.demo@example");
		accounts.updateProfile(account, profile);
		business = accounts.get(account);
		plan = plans.all(account).stream().collect(Collectors.toMap(AmcPlan::name, Function.identity()));
		sunil = technicians.add(account, "Sunil Pawar", "+919000070001");
		imran = technicians.add(account, "Imran Shaikh", "+919000070002");

		endingThisMonth();
		lapsed();
		renewedLately();
		inForce();
		enquiries();
		labelsToFinish();
		log.info("Demo AC and RO service business created: sign in as {} / {}", demo.email(), demo.password());
	}

	/** Six contracts that end within 30 days; reminders went out on schedule and two customers answered. */
	private void endingThisMonth() {
		Customer sunita = customer(1, "Sunita Deshpande", "Kothrud", "B-12, Sukhwani Park, Paud Road", 400);
		Appliance lg = appliance(sunita, ApplianceKind.SPLIT_AC, "LG", "PS-Q19YNZE", "312KAQX0R551", "1.5 TR", "R32",
				"03/2022", "bedroom", sunil, 400);
		Contract sunitaAmc = contract(sunita, lg, "AC Care AMC", null, today.plusDays(9), "TECHNICIAN", "UPI");
		history(sunitaAmc, today);
		visits(sunitaAmc, imran);
		ServiceRequest asked = requestService.forContract(sunitaAmc, ServiceRequest.RENEWAL, "LINK",
				plan.get("AC Care AMC").id(), null, null, null);
		backdateRequest(asked.id(), at(today.minusDays(1), 19, 42));

		Customer anand = customer(2, "Anand Kulkarni", "Karve Nagar", "Flat 7, Ashirwad Apartments, Hingne Road", 380);
		Appliance voltas = appliance(anand, ApplianceKind.SPLIT_AC, "Voltas", "183V CZTT", "4B12065521", "1.5 TR", "R32",
				"02/2023", "hall", sunil, 380);
		Contract anandAmc = contract(anand, voltas, "AC Comprehensive AMC", null, today.plusDays(3), "TECHNICIAN", "CASH");
		history(anandAmc, today);
		visits(anandAmc, sunil);

		Customer meera = customer(3, "Meera Joshi", "Erandwane", "14 Gulmohar Society, Law College Road", 410);
		Appliance kent = appliance(meera, ApplianceKind.RO, "Kent", "Grand Plus", "KGP2207781", null, null, "11/2022",
				"kitchen", imran, 410);
		Contract meeraAmc = contract(meera, kent, "RO Care AMC", null, today.plusDays(15), "TECHNICIAN", "UPI");
		history(meeraAmc, today);
		visits(meeraAmc, imran);

		// Last year's rate: the reminders quote this year's ₹1,499.
		Customer rohit = customer(4, "Rohit Patil", "Warje", "C-304, Ganga Kunj, Warje Malwadi", 420);
		Appliance daikin = appliance(rohit, ApplianceKind.SPLIT_AC, "Daikin", "FTKF35UV16V", "E004417", "1.0 TR", "R32",
				"05/2021", "bedroom", null, 420);
		Contract rohitAmc = contract(rohit, daikin, "AC Care AMC", new BigDecimal("1399"), today.plusDays(21), "OFFICE",
				"CASH");
		history(rohitAmc, today);
		visits(rohitAmc, sunil);

		Customer kavita = customer(5, "Kavita Shinde", "Bavdhan", "Row house 3, Green Meadows, Bavdhan Khurd", 390);
		Appliance aquaguard = appliance(kavita, ApplianceKind.RO, "Aquaguard", "Aura RO+UV", "AQA21K09934", null, null,
				"08/2021", "kitchen", imran, 390);
		Contract kavitaAmc = contract(kavita, aquaguard, "RO Gold AMC", null, today.plusDays(27), "TECHNICIAN", "UPI");
		history(kavitaAmc, today);
		visits(kavitaAmc, imran);

		// Ends today; the customer answered this morning's reminder with YES.
		Customer vikram = customer(6, "Vikram Gokhale", "Deccan", "Office 21, Sai Chambers, FC Road", 395);
		Appliance bluestar = appliance(vikram, ApplianceKind.SPLIT_AC, "Blue Star", "IC524DATU", "BS24D0193", "2.0 TR",
				"R32", "01/2022", "office cabin", sunil, 395);
		Contract vikramAmc = contract(vikram, bluestar, "AC Comprehensive AMC", null, today, "TECHNICIAN", "BANK");
		history(vikramAmc, today);
		visits(vikramAmc, sunil);
		OffsetDateTime yes = min(at(today, 10, 22), now.minusMinutes(20));
		received(vikram, "Yes", yes);
		ServiceRequest renewal = requestService.forContract(vikramAmc, ServiceRequest.RENEWAL, "WHATSAPP", null, null,
				null, null);
		backdateRequest(renewal.id(), yes);
		sent(Outgoing.whatsApp(account, vikram.phone(), whatsApp.renewalNoted(account, vikramAmc))
			.about("request", renewal.id()), yes.plusSeconds(4));
	}

	/** Contracts that ended without a renewal, one of them with a customer who asked to stop. */
	private void lapsed() {
		Customer neha = customer(7, "Neha Bhosale", "Sinhagad Road", "A-9, Anand Nagar, Sinhagad Road", 375);
		Appliance hitachi = appliance(neha, ApplianceKind.SPLIT_AC, "Hitachi", "RSOG518HCEA", "HT18C55120", "1.5 TR",
				"R410A", "04/2020", "bedroom", sunil, 375);
		Contract nehaAmc = contract(neha, hitachi, "AC Care AMC", null, today.minusDays(5), "TECHNICIAN", "CASH");
		history(nehaAmc, today);
		visits(nehaAmc, sunil);

		Customer suresh = customer(8, "Suresh Kale", "Hingne", "House 41, Hingne Budruk", 360);
		Appliance livpure = appliance(suresh, ApplianceKind.RO, "Livpure", "Glo Star", "LPG19A20871", null, null,
				"10/2019", "kitchen", imran, 360);
		Contract sureshAmc = contract(suresh, livpure, "RO Care AMC", null, today.minusDays(12), "TECHNICIAN", "UPI");
		history(sureshAmc, today);
		visits(sureshAmc, imran);

		Customer pooja = customer(9, "Pooja Sathe", "Aundh", "D-502, Pride Residency, ITI Road", 410);
		Appliance samsung = appliance(pooja, ApplianceKind.SPLIT_AC, "Samsung", "AR18BY5YATB", "0D7KPAK700412", "1.5 TR",
				"R32", "03/2021", "living room", null, 410);
		Contract poojaAmc = contract(pooja, samsung, "AC Care AMC", null, today.minusDays(40), "OFFICE", "UPI");
		history(poojaAmc, today);
		visits(poojaAmc, sunil);

		Customer ganesh = customer(10, "Ganesh More", "Paud Road", "12, Shivtirth Nagar, Paud Road", 385);
		Appliance pureit = appliance(ganesh, ApplianceKind.RO, "Pureit", "Copper+ RO", "HUL20C77345", null, null,
				"06/2020", "kitchen", imran, 385);
		Contract ganeshAmc = contract(ganesh, pureit, "RO Care AMC", null, today.minusDays(20), "TECHNICIAN", "CASH");
		history(ganeshAmc, today.minusDays(13));
		visits(ganeshAmc, imran);
		OffsetDateTime stop = at(today.minusDays(13), 18, 5);
		received(ganesh, "Stop", stop);
		customers.setOptedOut(account, ganesh.id(), true);
		sent(Outgoing.whatsApp(account, ganesh.phone(), "Done. You will not get reminders from " + business.name()
				+ " any more. Reply START to get them again."), stop.plusSeconds(3));
	}

	/** Renewals from the reminders: on WhatsApp, from the link, early, late and one upgrade. */
	private void renewedLately() {
		Customer asha = customer(11, "Asha Deshmukh", "Kothrud", "5 Mayur Colony, Kothrud", 400);
		Appliance lg = appliance(asha, ApplianceKind.SPLIT_AC, "LG", "RS-Q14ENZE", "205KAKM1Q884", "1.0 TR", "R32",
				"12/2021", "bedroom", sunil, 400);
		Contract ashaOld = contract(asha, lg, "AC Care AMC", null, today.minusDays(6), "TECHNICIAN", "UPI");
		history(ashaOld, today.minusDays(3));
		visits(ashaOld, sunil);
		OffsetDateTime yes = at(today.minusDays(6), 10, 47);
		received(asha, "yes", yes);
		sent(Outgoing.whatsApp(account, asha.phone(), whatsApp.renewalNoted(account, ashaOld)), yes.plusSeconds(4));
		renew(ashaOld, "AC Care AMC", today.minusDays(2), "UPI", "WHATSAPP", yes);

		Customer prashant = customer(12, "Prashant Naik", "Baner", "B-1101, Elite Towers, Baner Road", 360);
		Appliance voltas = appliance(prashant, ApplianceKind.SPLIT_AC, "Voltas", "185V DZX", "4B18077302", "1.5 TR", "R32",
				"04/2022", "master bedroom", sunil, 360);
		Contract prashantOld = contract(prashant, voltas, "AC Comprehensive AMC", null, today.plusDays(8), "TECHNICIAN",
				"UPI");
		history(prashantOld, today.minusDays(6));
		visits(prashantOld, sunil);
		renew(prashantOld, "AC Comprehensive AMC", today.minusDays(5), "UPI", "LINK");

		// Upgraded from RO Care to RO Gold when renewing.
		Customer shalini = customer(13, "Shalini Rao", "Aundh", "17 Sindh Society, Aundh", 370);
		Appliance kent = appliance(shalini, ApplianceKind.RO, "Kent", "Supreme Extra", "KSE2008112", null, null,
				"09/2020", "kitchen", imran, 370);
		Contract shaliniOld = contract(shalini, kent, "RO Care AMC", null, today.minusDays(3), "TECHNICIAN", "CASH");
		history(shaliniOld, today.minusDays(1));
		visits(shaliniOld, imran);
		renew(shaliniOld, "RO Gold AMC", today.minusDays(1), "CASH", "OFFICE");

		// Renewed on the phone; the money is still to be collected.
		Customer deepak = customer(14, "Deepak Jagtap", "Warje", "Flat 12, Shree Residency, Warje", 365);
		Appliance daikin = appliance(deepak, ApplianceKind.SPLIT_AC, "Daikin", "FTKL50UV16V", "E007783", "1.5 TR", "R32",
				"03/2022", "hall", null, 365);
		Contract deepakOld = contract(deepak, daikin, "AC Care AMC", null, today.minusDays(20), "OFFICE", "CASH");
		history(deepakOld, today.minusDays(18));
		visits(deepakOld, sunil);
		renew(deepakOld, "AC Care AMC", today.minusDays(18), null, "OFFICE");

		Customer farida = customer(15, "Farida Khan", "Kondhwa", "E-7, Kumar Prithvi, Kondhwa", 380);
		Appliance bluestar = appliance(farida, ApplianceKind.SPLIT_AC, "Blue Star", "IC318DNU", "BS18N2280", "1.5 TR",
				"R32", "05/2022", "bedroom", sunil, 380);
		Contract faridaOld = contract(farida, bluestar, "AC Care AMC", null, today.minusDays(12), "TECHNICIAN", "UPI");
		history(faridaOld, today.minusDays(12));
		visits(faridaOld, sunil);
		renew(faridaOld, "AC Care AMC", today.minusDays(12), "UPI", "LINK");

		// Earlier in the quarter.
		Customer milind = customer(16, "Milind Apte", "Shivajinagar", "8 Model Colony, Shivajinagar", 420);
		Appliance hitachi = appliance(milind, ApplianceKind.SPLIT_AC, "Hitachi", "RAW318HEDO", "HT18E03371", "1.5 TR",
				"R32", "02/2021", "study", null, 420);
		Contract milindOld = contract(milind, hitachi, "AC Care AMC", null, today.minusDays(50), "OFFICE", "CASH");
		history(milindOld, today.minusDays(52));
		visits(milindOld, sunil);
		renew(milindOld, "AC Care AMC", today.minusDays(52), "CASH", "OFFICE");

		Customer jyoti = customer(17, "Jyoti Kamble", "Erandwane", "C-2, Mehendale Garage Road", 430);
		Appliance aquaguard = appliance(jyoti, ApplianceKind.RO, "Aquaguard", "Marvel NXT", "AQM20B13377", null, null,
				"01/2021", "kitchen", imran, 430);
		Contract jyotiOld = contract(jyoti, aquaguard, "RO Care AMC", null, today.minusDays(75), "TECHNICIAN", "UPI");
		history(jyotiOld, today.minusDays(70));
		visits(jyotiOld, imran);
		renew(jyotiOld, "RO Care AMC", today.minusDays(70), "UPI", "WHATSAPP");

		Customer sameer = customer(18, "Sameer Kulkarni", "Bavdhan", "A-203, Sun Gardens, Bavdhan", 440);
		Appliance lgHall = appliance(sameer, ApplianceKind.SPLIT_AC, "LG", "PS-Q18KNXE", "203KAJF2C110", "1.5 TR", "R32",
				"07/2020", "hall", sunil, 440);
		Contract sameerOld = contract(sameer, lgHall, "AC Comprehensive AMC", null, today.minusDays(85), "TECHNICIAN",
				"UPI");
		history(sameerOld, today.minusDays(80));
		visits(sameerOld, sunil);
		renew(sameerOld, "AC Comprehensive AMC", today.minusDays(80), "UPI", "LINK");
	}

	/** Contracts well inside their year, with visits done and the next ones booked. */
	private void inForce() {
		Customer rahul = customer(19, "Rahul Kadam", "Kothrud", "Plot 22, Bhusari Colony, Kothrud", 300);
		Appliance daikin = appliance(rahul, ApplianceKind.SPLIT_AC, "Daikin", "FTKM50UV16V", "E009914", "1.5 TR", "R32",
				"04/2023", "bedroom", sunil, 245);
		Contract rahulAc = contract(rahul, daikin, "AC Care AMC", null, today.plusDays(120), "TECHNICIAN", "UPI");
		visits(rahulAc, sunil);
		Appliance kent = appliance(rahul, ApplianceKind.RO, "Kent", "Pearl", "KPL2301457", null, null, "02/2023",
				"kitchen", imran, 165);
		Contract rahulRo = contract(rahul, kent, "RO Care AMC", null, today.plusDays(200), "TECHNICIAN", "UPI");
		visits(rahulRo, imran);
		book(rahulAc, "LINK", at(today.minusDays(3), 19, 15), today, "Morning", sunil, null);

		Customer snehal = customer(20, "Snehal Wagh", "Bavdhan", "Flat 4B, Ramnagar Colony, Bavdhan", 120);
		Appliance hitachi = appliance(snehal, ApplianceKind.SPLIT_AC, "Hitachi", "RAPG318HHEA", "HT18H11905", "1.5 TR",
				"R32", "03/2024", "bedroom", sunil, 115);
		Contract snehalAmc = contract(snehal, hitachi, "AC Comprehensive AMC", null, today.plusDays(250), "TECHNICIAN",
				"UPI");
		visits(snehalAmc, sunil);
		book(snehalAmc, "LINK", at(today.minusDays(2), 21, 30), today.plusDays(2), "Morning", sunil,
				"Remote not working sometimes");

		Customer arti = customer(21, "Arti Gaikwad", "Erandwane", "B-6, Prabhat Road Lane 4", 70);
		Appliance livpure = appliance(arti, ApplianceKind.RO, "Livpure", "Zinger Copper", "LPZ24C30019", null, null,
				"06/2024", "kitchen", imran, 65);
		// The first service of the year falls due in two days: today's reminder run picks it up.
		contract(arti, livpure, "RO Care AMC", null, today.plusDays(320), "TECHNICIAN", "CASH");

		Customer nitin = customer(22, "Nitin Chavan", "Sinhagad Road", "Flat 9, Vrundavan, Vadgaon Budruk", 330);
		Appliance samsung = appliance(nitin, ApplianceKind.SPLIT_AC, "Samsung", "AR18CYLZABE", "0E5JPAN201177", "1.5 TR",
				"R32", "02/2023", "bedroom", null, 320);
		Contract nitinAmc = contract(nitin, samsung, "AC Care AMC", null, today.plusDays(45), "OFFICE", "UPI");
		visits(nitinAmc, imran);
		OffsetDateTime reply = at(today.minusDays(1), 11, 4);
		received(nitin, "service", reply);
		sent(Outgoing.whatsApp(account, nitin.phone(), "Thank you! We will call you to fix a time. Or pick a day yourself: "
				+ reminderService.link(nitinAmc)), reply.plusSeconds(3));
		book(nitinAmc, "WHATSAPP", reply, today.plusDays(1), "Afternoon", imran, null);

		Customer swati = customer(23, "Swati Mane", "Hingne", "11 Sai Krupa, Hingne Khurd", 280);
		Appliance voltas = appliance(swati, ApplianceKind.WINDOW_AC, "Voltas", "123 LZF", "VW12L09918", "1.0 TR", "R32",
				"03/2021", "bedroom", sunil, 275);
		Contract swatiAmc = contract(swati, voltas, "AC Care AMC", null, today.plusDays(90), "TECHNICIAN", "CASH");
		visits(swatiAmc, sunil);
		ServiceRequest visit = requestService.forContract(swatiAmc, ServiceRequest.SERVICE, "LINK", null,
				today.plusDays(3), "Evening", "AC not cooling properly since last week");
		backdateRequest(visit.id(), min(at(today, 8, 50), now.minusMinutes(45)));

		// A water heater the technician noted on a visit: no AMC on it yet.
		Customer manoj = customer(24, "Manoj Thorat", "Karve Nagar", "7 Hingne Home Colony, Karve Nagar", 200);
		Appliance lgAc = appliance(manoj, ApplianceKind.SPLIT_AC, "LG", "US-Q19YNZE", "306KAGH4P017", "1.5 TR", "R32",
				"06/2023", "hall", sunil, 195);
		Contract manojAmc = contract(manoj, lgAc, "AC Care AMC", null, today.plusDays(170), "TECHNICIAN", "UPI");
		visits(manojAmc, sunil);
		appliance(manoj, ApplianceKind.WATER_HEATER, "Racold", "Eterno Pro 15L", "RC15E220871", "15 L", null, "01/2022",
				"bathroom", null, 195);

		// Moved away: the contract was closed rather than chased.
		Customer harish = customer(25, "Harish Shetty", "Deccan", "Flat 3, Bhandarkar Road", 400);
		Appliance pureit = appliance(harish, ApplianceKind.RO, "Pureit", "Eco Water Saver", "HUL19E40022", null, null,
				"04/2019", "kitchen", null, 400);
		Contract harishAmc = contract(harish, pureit, "RO Care AMC", null, today.minusDays(30), "OFFICE", "CASH");
		history(harishAmc, today.minusDays(37));
		contracts.cancel(account, harishAmc.id());
		customers.update(account, harish.id(), harish.name(), harish.address(), harish.area(), "Moved to Bengaluru");
	}

	/** Someone new wrote on WhatsApp: a customer record and an enquiry to call back. */
	private void enquiries() {
		OffsetDateTime wrote = min(at(today, 9, 12), now.minusMinutes(35));
		String text = "Hi, my Whirlpool AC is leaking water from the indoor unit. Can someone come tomorrow? Shivajinagar";
		Customer kiran = customers.findOrCreate(account, "+919000080026", "Kiran", wrote);
		received(kiran, text, wrote);
		long id = requestService.enquiry(account, kiran.id(), text);
		backdateRequest(id, wrote);
		sent(Outgoing.whatsApp(account, kiran.phone(), AmcWhatsApp.enquiryReply(business)), wrote.plusSeconds(3));
	}

	/** Two label photos the office has to finish: no customer number, and a label read with doubts. */
	private void labelsToFinish() {
		scan(sunil, LabelSamples.splitAc("Daikin", "FTKM50UV16V", "E010277", "1.5 TR", "R32", "07/2024"), "AMC 1499 hall",
				ApplianceKind.SPLIT_AC, "Daikin", "FTKM50UV16V", "E010277", "1.5 TR", "R32", "07/2024", "0.92",
				min(at(today, 11, 40), now.minusMinutes(40)));
		scan(imran, LabelSamples.ro("Kent", "Grand Plus", "KGP2411208", "09/2024"),
				"9000080027 Lakshmi Iyer AMC 2499 paid kitchen", ApplianceKind.RO, "Kent", "Grand Plus", null, null, null,
				"09/2024", "0.58", min(at(today, 10, 5), now.minusMinutes(90)));
	}

	// ---------------------------------------------------------------- building blocks

	private Customer customer(int number, String name, String area, String address, int sinceDays) {
		String phone = "+91900008" + String.format("%04d", number);
		Customer customer = customers.findOrCreate(account, phone, name, at(today.minusDays(sinceDays), 12, 0));
		customers.update(account, customer.id(), name, address, area, null);
		return customers.get(account, customer.id());
	}

	/**
	 * An appliance as the technician saved it: from a label photo with the customer's number in the
	 * caption, or typed in by the office when {@code technician} is null.
	 */
	private Appliance appliance(Customer customer, ApplianceKind kind, String brand, String model, String serial,
			String capacity, String gas, String made, String location, Long technician, int sinceDays) {
		OffsetDateTime when = at(today.minusDays(sinceDays), 12, 20);
		Long fileId = null;
		if (technician != null && (kind == ApplianceKind.RO || kind == ApplianceKind.SPLIT_AC)) {
			byte[] photo = kind == ApplianceKind.RO ? LabelSamples.ro(brand, model, serial, made)
					: LabelSamples.splitAc(brand, model, serial, capacity, gas, made);
			fileId = files.save(account, photo, "image/jpeg", "label.jpg").id();
		}
		long id = appliances.create(account, customer.id(), kind, brand, model, serial, capacity, gas, made, location,
				fileId, when);
		if (fileId != null) {
			String caption = customer.phone().substring(3) + " " + customer.name().split(" ")[1] + " AMC " + location;
			long scanId = scans.create(new LabelScans.NewScan(account, technician, fileId, caption, LabelScan.READ, null,
					kind.name(), brand, model, serial, capacity, gas, made, customer.phone(), customer.name().split(" ")[1],
					true, null, new BigDecimal("0.94"), when));
			scans.link(scanId, id);
		}
		return appliances.get(account, id);
	}

	/** A twelve-month contract on a plan that ends on the given day, paid when it was sold. */
	private Contract contract(Customer customer, Appliance appliance, String planName, BigDecimal price, LocalDate endsOn,
			String source, String paidBy) {
		AmcPlan chosen = plan.get(planName);
		LocalDate startsOn = endsOn.minusMonths(chosen.months()).plusDays(1);
		BigDecimal amount = price != null ? price : chosen.price();
		long id = contracts.create(account, customer.id(), appliance.id(), chosen.id(), chosen.name(), amount, startsOn,
				endsOn, chosen.visits(), source, at(min(startsOn, today), 12, 30));
		if (paidBy != null) {
			contracts.recordPayment(account, id, amount, min(startsOn, today), paidBy);
		}
		return contracts.get(account, id);
	}

	/** The renewal reminders that fell due up to a day, each sent at 10 that morning. */
	private void history(Contract contract, LocalDate until) {
		Customer customer = customers.get(account, contract.customerId());
		Appliance appliance = appliances.get(account, contract.applianceId());
		BigDecimal price = reminderService.renewalPrice(account, contract);
		String link = reminderService.link(contract);
		for (int offset : OFFSETS) {
			LocalDate dueOn = contract.endsOn().plusDays(offset);
			if (dueOn.isAfter(until) || dueOn.isAfter(today)) {
				break;
			}
			OffsetDateTime when = min(at(dueOn, 10, 0), now.minusMinutes(60));
			OutboundMessage message = sent(Outgoing.whatsApp(account, customer.phone(),
					ReminderMessages.renewal(business, customer, appliance, contract, price, dueOn, link))
				.about("contract", contract.id())
				.template("amc_renewal_reminder", customer.greetingName(), appliance.describe(),
						contract.endsOn().toString(), IndianMoney.rupees(price), link), when);
			reminders.record(account, contract.id(), "RENEWAL", offset, dueOn, when, message.id());
		}
	}

	/** Service visits of the contract that are well past their due day, done by the technician. */
	private void visits(Contract contract, long technician) {
		for (ReminderService.Visit visit : ReminderService.visitDates(contract)) {
			LocalDate done = visit.dueOn().plusDays(2);
			if (!done.plusDays(5).isBefore(today)) {
				break;
			}
			long id = requests.create(account, contract.customerId(), contract.id(), contract.applianceId(),
					ServiceRequest.SERVICE, "OFFICE", null, visit.dueOn(), null, null, at(visit.dueOn().minusDays(3), 11, 0));
			requests.schedule(account, id, done, technician, at(visit.dueOn().minusDays(2), 16, 0));
			requests.finish(account, id, ServiceRequest.DONE, done, at(done, 17, 30));
			reminders.record(account, contract.id(), "SERVICE", visit.number(), visit.dueOn(),
					at(visit.dueOn().minusDays(3), 10, 0), null);
		}
	}

	/** A visit the customer asked for and the office booked, with the confirmation they got. */
	private void book(Contract contract, String source, OffsetDateTime asked, LocalDate on, String slot, long technician,
			String note) {
		ServiceRequest request = requestService.forContract(contract, ServiceRequest.SERVICE, source, null, on, slot, note);
		backdateRequest(request.id(), asked);
		requestService.schedule(account, request.id(), on, technician);
		OffsetDateTime booked = min(asked.plusHours(15), now.minusMinutes(10));
		String phone = customers.get(account, contract.customerId()).phone();
		jdbc.sql("UPDATE service_request SET updated_at = ? WHERE id = ?").params(booked, request.id()).update();
		jdbc.sql("""
				UPDATE outbound_message SET created_at = ?, status_updated_at = ?
				WHERE related_type = 'request' AND related_id = ? AND recipient = ?
				""").params(booked, booked, request.id(), phone).update();
	}

	/**
	 * Renewed on a day: the new year, the payment, the thank-you message and the request it closed
	 * (asked on the link or on WhatsApp the evening before, unless given).
	 */
	private void renew(Contract old, String planName, LocalDate on, String paidBy, String askedBy) {
		renew(old, planName, on, paidBy, askedBy, at(on.minusDays(1), 20, 10));
	}

	private void renew(Contract old, String planName, LocalDate on, String paidBy, String askedBy, OffsetDateTime asked) {
		AmcPlan chosen = plan.get(planName);
		if (!"OFFICE".equals(askedBy)) {
			long requestId = requests.create(account, old.customerId(), old.id(), old.applianceId(), ServiceRequest.RENEWAL,
					askedBy, chosen.id(), null, null, null, asked);
			requests.finish(account, requestId, ServiceRequest.DONE, on, at(on, 12, 55));
		}
		LocalDate start = old.endsOn().isBefore(on) ? on : old.endsOn().plusDays(1);
		LocalDate end = start.plusMonths(chosen.months()).minusDays(1);
		OffsetDateTime when = min(at(on, 13, 0), now.minusMinutes(15));
		long id = contracts.create(account, old.customerId(), old.applianceId(), chosen.id(), chosen.name(), chosen.price(),
				start, end, chosen.visits(), "RENEWAL", when);
		if (paidBy != null) {
			contracts.recordPayment(account, id, chosen.price(), on, paidBy);
		}
		contracts.markRenewed(old.id(), id);
		Contract renewed = contracts.get(account, id);
		visits(renewed, "RO".equals(chosen.family()) ? imran : sunil);
		Customer customer = customers.get(account, old.customerId());
		Appliance appliance = appliances.get(account, old.applianceId());
		sent(Outgoing.whatsApp(account, customer.phone(),
				ReminderMessages.renewalConfirmed(business, customer, appliance, renewed)).about("contract", id),
				when.plusMinutes(1));
	}

	private void scan(long technician, byte[] photo, String caption, ApplianceKind kind, String brand, String model,
			String serial, String capacity, String gas, String made, String confidence, OffsetDateTime when) {
		StoredFile file = files.save(account, photo, "image/jpeg", "label.jpg");
		CaptionParser.Caption parsed = CaptionParser.parse(caption);
		long id = scans.create(new LabelScans.NewScan(account, technician, file.id(), caption, LabelScan.READ, null,
				kind.name(), brand, model, serial, capacity, gas, made, parsed.phone(), parsed.name(), parsed.amcSold(),
				parsed.amount(), new BigDecimal(confidence), when));
		String phone = inbound(technician, caption, file, when);
		LabelService.Received received = new LabelService.Received(scans.get(account, id), null, parsed);
		sent(Outgoing.whatsApp(account, phone, whatsApp.labelReply(account, received)).about("label", id),
				when.plusSeconds(6));
	}

	/** The technician's photo as it arrived on WhatsApp; returns their number. */
	private String inbound(long technician, String caption, StoredFile file, OffsetDateTime when) {
		String phone = technician == sunil ? "+919000070001" : "+919000070002";
		String name = technician == sunil ? "Sunil Pawar" : "Imran Shaikh";
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(account, "demo-" + UUID.randomUUID(), phone,
				SimulatorController.PLATFORM_NUMBER, name, "image", caption, null, "image/jpeg", file.originalName(), file.id(),
				null, when)).orElseThrow();
		inbound.finish(id, InboundMessage.PROCESSED, null, when);
		return phone;
	}

	private void received(Customer customer, String text, OffsetDateTime when) {
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(account, "demo-" + UUID.randomUUID(), customer.phone(),
				SimulatorController.PLATFORM_NUMBER, customer.name(), "text", text, null, null, null, null, null, when))
			.orElseThrow();
		inbound.finish(id, InboundMessage.PROCESSED, null, when);
	}

	/** Sends through the normal path (the simulator, unless WhatsApp is set up) and dates it. */
	private OutboundMessage sent(Outgoing message, OffsetDateTime when) {
		OutboundMessage sent = messenger.send(message);
		jdbc.sql("UPDATE outbound_message SET created_at = ?, status_updated_at = ? WHERE id = ?")
			.params(when, when, sent.id())
			.update();
		return sent;
	}

	/** Gives a request, and the owner's notification about it, the time the customer asked. */
	private void backdateRequest(long requestId, OffsetDateTime when) {
		jdbc.sql("UPDATE service_request SET created_at = ?, updated_at = ? WHERE id = ?")
			.params(when, when, requestId)
			.update();
		jdbc.sql("""
				UPDATE outbound_message SET created_at = ?, status_updated_at = ?
				WHERE related_type = 'request' AND related_id = ?
				""").params(when.plusSeconds(2), when.plusSeconds(2), requestId).update();
	}

	private OffsetDateTime at(LocalDate day, int hour, int minute) {
		return day.atTime(hour, minute).atZone(clock.getZone()).toOffsetDateTime();
	}

	private static OffsetDateTime min(OffsetDateTime a, OffsetDateTime b) {
		return a.isBefore(b) ? a : b;
	}

	private static LocalDate min(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

}
