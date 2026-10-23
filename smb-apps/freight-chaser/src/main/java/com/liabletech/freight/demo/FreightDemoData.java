package com.liabletech.freight.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.liabletech.freight.chase.FollowUp;
import com.liabletech.freight.chase.FollowUps;
import com.liabletech.freight.fleet.Driver;
import com.liabletech.freight.fleet.Fleet;
import com.liabletech.freight.invoice.GstMode;
import com.liabletech.freight.invoice.InvoiceService;
import com.liabletech.freight.invoice.NewInvoiceForm;
import com.liabletech.freight.invoice.PaymentForm;
import com.liabletech.freight.party.Parties;
import com.liabletech.freight.party.PartyForm;
import com.liabletech.freight.pod.PodService;
import com.liabletech.freight.pod.Pods;
import com.liabletech.freight.trip.Trip;
import com.liabletech.freight.trip.TripForm;
import com.liabletech.freight.trip.Trips;
import com.liabletech.freight.whatsapp.PodSampleImage;
import com.liabletech.platform.AppProperties;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.account.ProfileForm;
import com.liabletech.platform.account.SignupService;
import com.liabletech.platform.account.Users;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;

/**
 * A demo fleet with trips at every stage, created on first start when {@code app.demo.enabled}
 * is on. Names, numbers and GSTINs are made up.
 */
@Component
public class FreightDemoData implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(FreightDemoData.class);

	private final AppProperties properties;

	private final Users users;

	private final SignupService signups;

	private final Accounts accounts;

	private final Fleet fleet;

	private final Parties parties;

	private final Trips trips;

	private final PodService podService;

	private final InvoiceService invoices;

	private final FollowUps followUps;

	private final FileStore files;

	private final Clock clock;

	public FreightDemoData(AppProperties properties, Users users, SignupService signups, Accounts accounts, Fleet fleet,
			Parties parties, Trips trips, PodService podService, InvoiceService invoices, FollowUps followUps,
			FileStore files, Clock clock) {
		this.properties = properties;
		this.users = users;
		this.signups = signups;
		this.accounts = accounts;
		this.fleet = fleet;
		this.parties = parties;
		this.trips = trips;
		this.podService = podService;
		this.invoices = invoices;
		this.followUps = followUps;
		this.files = files;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		AppProperties.Demo demo = properties.demo();
		if (!demo.enabled() || users.findByEmail(demo.email()).isPresent()) {
			return;
		}
		LocalDate today = LocalDate.now(clock);
		long account = signups.createDemo("Shree Ganesh Roadways", "Suresh Patil", "+919000010000", demo.email(),
				demo.password());
		ProfileForm profile = ProfileForm.of(accounts.get(account));
		profile.setAddress("Plot 14, Transport Nagar, Nigdi, Pune 411044");
		profile.setGstin("27ABCDE1234F1Z5");
		profile.setPan("ABCDE1234F");
		profile.setUpiId("shreeganesh@demo");
		profile.setBankAccountName("Shree Ganesh Roadways");
		profile.setBankAccountNumber("000123456789");
		profile.setBankIfsc("DEMO0001234");
		profile.setBankName("Demo Co-operative Bank, Nigdi");
		accounts.updateProfile(account, profile);

		long truck1 = fleet.addVehicle(account, "MH12AB1234", "32 ft MXL container");
		long truck2 = fleet.addVehicle(account, "MH14CD5678", "Tata 1109, 19 ft");
		long truck3 = fleet.addVehicle(account, "MH12EF9012", "Eicher 14 ft");
		Driver ramesh = fleet.driver(account, fleet.addDriver(account, "Ramesh Yadav", "+919000010001", truck1));
		Driver imran = fleet.driver(account, fleet.addDriver(account, "Imran Shaikh", "+919000010002", truck2));
		Driver gurpreet = fleet.driver(account, fleet.addDriver(account, "Gurpreet Singh", "+919000010003", truck3));

		long sharma = party(account, "Sharma Roadlines", "Vijay Sharma", "+919000020001", "accounts@sharma-roadlines.example",
				"27AAACS1234B1Z2", "Market Yard, Gultekdi, Pune", 30);
		long omSai = party(account, "Om Sai Logistics", "Prakash Jadhav", "+919000020002", "payments@omsai-logistics.example",
				"27AABCO5678C1Z9", "MIDC Bhosari, Pune", 45);
		long vardhman = party(account, "Vardhman Transport Co.", "Mahesh Jain", "+919000020003",
				"billing@vardhman-transport.example", "24AAAFV4321D1Z4", "Ring Road, Surat", 15);

		// On the road: the simulator's sample POD completes LR 4521.
		trip(account, "4521", today.minusDays(2), sharma, truck1, ramesh, "Pune", "Nagpur", "Vidarbha Traders", "Rice",
				"42000", "10000", "0");
		trip(account, "4518", today.minusDays(1), omSai, truck2, imran, "Pune", "Hyderabad", "Deccan Pharma Stores",
				"Pharma cartons", "38500", "0", "0");

		// Delivered with a clear POD: ready to bill.
		long t4502 = trip(account, "4502", today.minusDays(8), sharma, truck3, gurpreet, "Chakan", "Bengaluru",
				"Karnataka Auto Parts", "Auto components", "55000", "15000", "2000");
		pod(account, t4502, gurpreet, today.minusDays(5), true, "Recd 180 boxes OK", 0.94);

		// Delivered, but the photo shows no stamp: waiting for the owner's check.
		long t4499 = trip(account, "4499", today.minusDays(6), vardhman, truck2, imran, "Pune", "Indore",
				"Malwa Textiles", "Yarn bales", "47000", "0", "0");
		pod(account, t4499, imran, today.minusDays(3), false, "Recd", 0.72);

		// Billed and overdue.
		long t4410 = deliveredTrip(account, "4410", today.minusDays(46), sharma, truck1, ramesh, "Pune", "Raipur",
				"Chhattisgarh Steel Traders", "Steel coils", "61000", "20000", today.minusDays(42));
		long t4415 = deliveredTrip(account, "4415", today.minusDays(45), sharma, truck3, gurpreet, "Pune", "Nagpur",
				"Orange City Distributors", "FMCG cartons", "36000", "0", today.minusDays(42));
		long inv1 = invoice(account, sharma, List.of(t4410, t4415), today.minusDays(40), GstMode.NONE);
		followUps.record(account, inv1, OffsetDateTime.now(clock).minusDays(3), FollowUp.NOTE, null,
				"Called Vijay ji. Says payment is with their accounts team.", null);

		long t4380 = deliveredTrip(account, "4380", today.minusDays(80), omSai, truck2, imran, "Pune", "Chennai",
				"Coromandel Agencies", "Machinery parts", "72000", "15000", today.minusDays(76));
		long inv2 = invoice(account, omSai, List.of(t4380), today.minusDays(75), GstMode.RCM);
		payment(account, inv2, today.minusDays(20), "20000", "0", "NEFT", "UTR SBIN0001234567");

		// Paid: these show how long each party really takes.
		long t4301 = deliveredTrip(account, "4301", today.minusDays(56), vardhman, truck1, ramesh, "Pune", "Surat",
				"Vardhman Transport Co.", "Grey fabric", "33000", "0", today.minusDays(52));
		long inv3 = invoice(account, vardhman, List.of(t4301), today.minusDays(50), GstMode.FORWARD);
		payment(account, inv3, today.minusDays(15), "34320", "330", "RTGS", "UTR HDFC0007654321");

		long t4350 = deliveredTrip(account, "4350", today.minusDays(66), sharma, truck3, gurpreet, "Pune", "Belgaum",
				"Belgaum Sugar Agencies", "Sugar bags", "29000", "5000", today.minusDays(62));
		long inv4 = invoice(account, sharma, List.of(t4350), today.minusDays(60), GstMode.NONE);
		payment(account, inv4, today.minusDays(8), "24000", "0", "UPI", "UPI 4521 9987");

		// A POD for a trip that was never entered: the office must create it from the photo.
		StoredFile stray = files.save(account, PodSampleImage.render("Om Sai Logistics", "4530", today.minusDays(4),
				today.minusDays(1), "MH14CD5678", "Pune", "Goa", "Konkan Foods", "Goa Hotel Supplies", "Spices", 90,
				"Recd 90 bags"), "image/jpeg", "pod-4530.jpg");
		podService.receiveKnown(account, stray, imran, "WHATSAPP",
				new Pods.Details("4530", today.minusDays(4), today.minusDays(1), "Konkan Foods", "Goa Hotel Supplies", "Pune",
						"Goa", "MH14CD5678", "90 bags", null, true, true, "Received 90 bags"),
				0.91, OffsetDateTime.now(clock).minusDays(1));
		log.info("Demo fleet created: sign in as {} / {}", demo.email(), demo.password());
	}

	private long party(long account, String name, String contact, String phone, String email, String gstin,
			String address, int creditDays) {
		PartyForm form = new PartyForm();
		form.setName(name);
		form.setContactPerson(contact);
		form.setPhone(phone);
		form.setEmail(email);
		form.setGstin(gstin);
		form.setAddress(address);
		form.setCreditDays(creditDays);
		return parties.create(account, form);
	}

	private long trip(long account, String lr, LocalDate lrDate, long partyId, long vehicleId, Driver driver,
			String from, String to, String consignee, String goods, String freight, String advance, String detention) {
		TripForm form = new TripForm();
		form.setLrNumber(lr);
		form.setLrDate(lrDate);
		form.setPartyId(partyId);
		form.setVehicleId(vehicleId);
		form.setDriverId(driver.id());
		form.setOrigin(from);
		form.setDestination(to);
		form.setConsignee(consignee);
		form.setGoods(goods);
		form.setFreightAmount(new BigDecimal(freight));
		form.setAdvanceAmount(new BigDecimal(advance));
		form.setDetentionAmount(new BigDecimal(detention));
		return trips.create(account, form, Trip.IN_TRANSIT);
	}

	private long deliveredTrip(long account, String lr, LocalDate lrDate, long partyId, long vehicleId, Driver driver,
			String from, String to, String consignee, String goods, String freight, String advance,
			LocalDate deliveredOn) {
		long id = trip(account, lr, lrDate, partyId, vehicleId, driver, from, to, consignee, goods, freight, advance, "0");
		pod(account, id, driver, deliveredOn, true, "Recd OK", 0.93);
		return id;
	}

	private void pod(long account, long tripId, Driver driver, LocalDate deliveredOn, boolean stamped, String remark,
			double confidence) {
		Trip trip = trips.get(account, tripId);
		String vehicle = fleet.vehicle(account, trip.vehicleId()).orElseThrow().registration();
		StoredFile file = files.save(account, PodSampleImage.render("Shree Ganesh Roadways", trip.lrNumber(), trip.lrDate(),
				deliveredOn, vehicle, trip.origin(), trip.destination(), "Deccan Agro Foods", trip.consignee(), trip.goods(),
				120, remark), "image/jpeg", "pod-" + trip.lrNumber() + ".jpg");
		podService.receiveKnown(account, file, driver, "WHATSAPP",
				new Pods.Details(trip.lrNumber(), trip.lrDate(), deliveredOn, "Deccan Agro Foods", trip.consignee(),
						trip.origin(), trip.destination(), vehicle, "120 packages", null, stamped, stamped, remark),
				confidence, deliveredOn.atTime(18, 30).atZone(clock.getZone()).toOffsetDateTime());
	}

	private long invoice(long account, long partyId, List<Long> tripIds, LocalDate date, GstMode mode) {
		NewInvoiceForm form = new NewInvoiceForm();
		form.setPartyId(partyId);
		form.setTripIds(tripIds);
		form.setInvoiceDate(date);
		form.setGstMode(mode);
		form.setGstRate(BigDecimal.valueOf(5));
		long id = invoices.create(account, form);
		invoices.send(account, id);
		return id;
	}

	private void payment(long account, long invoiceId, LocalDate on, String amount, String tds, String mode,
			String reference) {
		PaymentForm form = new PaymentForm();
		form.setReceivedOn(on);
		form.setAmount(new BigDecimal(amount));
		form.setTds(new BigDecimal(tds));
		form.setMode(mode);
		form.setReference(reference);
		form.setThankYou(false);
		invoices.recordPayment(account, invoiceId, form);
	}

}
