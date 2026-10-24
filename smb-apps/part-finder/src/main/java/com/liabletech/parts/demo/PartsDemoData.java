package com.liabletech.parts.demo;

import java.awt.Color;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.liabletech.parts.PartsNavigation;
import com.liabletech.parts.booking.Bookings;
import com.liabletech.parts.catalog.Part;
import com.liabletech.parts.catalog.Parts;
import com.liabletech.parts.catalog.StockCsv;
import com.liabletech.parts.enquiry.Enquiries;
import com.liabletech.parts.enquiry.Enquiry;
import com.liabletech.parts.enquiry.EnquiryService;
import com.liabletech.parts.enquiry.Identification;
import com.liabletech.parts.enquiry.PartMatcher;
import com.liabletech.parts.enquiry.QuoteOptions;
import com.liabletech.parts.enquiry.TextParser;
import com.liabletech.parts.mechanic.Mechanic;
import com.liabletech.parts.mechanic.Mechanics;
import com.liabletech.parts.whatsapp.PartSamples;
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
 * A fictional spare parts counter in Jaipur on the Pro plan: forty parts from made-up brands with
 * what they fit, seven mechanics, three weeks of answered enquiries for the reports, and today's
 * counter: a booking to hand over, a quote waiting, a part it did not have and two enquiries still
 * waiting. Nothing here calls the AI; enquiries are understood from their words, as without a key.
 */
@Component
public class PartsDemoData implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(PartsDemoData.class);

	/** part_number, name, category, brand, price, mrp, stock, rack, fits, alt_numbers. */
	private static final String STOCK = """
			part_number,name,category,brand,price,mrp,stock,rack,fits,alt_numbers
			BP-SWF-PS,Front brake pad set,BRAKE_PAD,ProStop,1150,1450,6,B3,Swift 2018-2024; Dzire 2017-2024; Baleno 2016-2024,PS-1123
			BP-SWF-GEN,Front brake pad set,BRAKE_PAD,Genuine,1890,2150,2,B3,Swift 2018-2024; Dzire 2017-2024,
			BS-SWF-R,Rear brake shoe set,BRAKE_SHOE,ProStop,890,1100,4,B4,Swift 2012-2024; Dzire 2012-2024,
			BP-I20-F,Front brake pad set,BRAKE_PAD,ProStop,1250,1590,3,B3,i20 2015-2023; i10 2014-2020,PS-2207
			BP-CRE-F,Front brake pad set,BRAKE_PAD,ProStop,1690,2100,0,B3,Creta 2016-2024; Verna 2017-2023,PS-3301
			BP-ALTO-F,Front brake pad set,BRAKE_PAD,ProStop,780,990,5,B2,Alto; WagonR 2010-2019,
			BP-BOL-F,Front brake pad set,BRAKE_PAD,ProStop,1450,1790,3,B5,Bolero 2011-2024; Scorpio 2014-2022,
			BP-PUL-F,Front disc pad,BRAKE_PAD,ProStop,320,420,10,D1,Pulsar; Apache,
			BS-ACT,Brake shoe set,BRAKE_SHOE,ProStop,260,340,15,D1,Activa; Jupiter; Splendor; HF Deluxe,
			CP-SWF,Clutch plate,CLUTCH_PLATE,DuraClutch,1750,2200,3,C1,Swift 2012-2024 (petrol); Dzire 2012-2024 (petrol),
			CP-ALTO,Clutch plate,CLUTCH_PLATE,DuraClutch,1250,1600,5,C1,Alto; WagonR 2010-2019,
			CP-BOL,Clutch plate,CLUTCH_PLATE,DuraClutch,2450,3050,0,C2,Bolero 2011-2024; Scorpio 2009-2014,
			CK-BOL,"Clutch kit (plate, cover, bearing)",CLUTCH_KIT,DuraClutch,6900,8400,1,C2,Bolero 2011-2024,
			CK-SWF,"Clutch kit (plate, cover, bearing)",CLUTCH_KIT,DuraClutch,4650,5600,2,C1,Swift 2012-2024 (petrol); Dzire 2012-2024 (petrol),
			OF-MS-110,Oil filter,OIL_FILTER,Filtra,180,240,40,F1,Swift; Dzire; Alto; WagonR; Baleno; Ertiga; Celerio,FL-16510
			OF-HY-210,Oil filter,OIL_FILTER,Filtra,210,280,25,F1,i10; i20; Creta (petrol); Verna (petrol); Santro,
			OF-BOL,Oil filter,OIL_FILTER,Filtra,320,420,12,F2,Bolero; Scorpio; Thar,
			AF-SWF,Air filter,AIR_FILTER,Filtra,340,450,12,F3,Swift 2018-2024; Dzire 2017-2024,
			AF-WR,Air filter,AIR_FILTER,Filtra,290,380,9,F3,WagonR; Alto; Celerio,
			AF-INN,Air filter,AIR_FILTER,Filtra,620,790,4,F3,Innova,
			FF-BOL,Diesel filter,FUEL_FILTER,Filtra,480,620,8,F2,Bolero; Scorpio,
			CF-CRE,Cabin AC filter,CABIN_FILTER,Filtra,390,520,6,F4,Creta; Venue; i20 2020-2024,
			SP-4W,Spark plug,SPARK_PLUG,Sparkon,160,210,60,E1,Swift (petrol); WagonR; Alto; Baleno,
			SP-2W,Spark plug,SPARK_PLUG,Sparkon,95,130,80,E1,Splendor; HF Deluxe; Passion; Platina; Shine,
			WB-22,Wiper blade 22 inch,WIPER_BLADE,ClearSweep,290,380,20,E3,Swift; Dzire; i20; City; Baleno,
			HB-H4,Headlight bulb H4 12V,BULB,VoltEdge,180,240,30,E2,Swift; Alto; WagonR; Bolero; i10; Splendor,
			SM-ALTO,Starter motor,STARTER_MOTOR,VoltEdge,3600,4400,1,E4,Alto; WagonR 2010-2019,
			ALT-BOL,Alternator,ALTERNATOR,VoltEdge,6800,8200,0,E4,Bolero 2011-2024,
			FB-SWF,Fan belt,BELT,Gripline,420,560,7,G1,Swift (petrol); Dzire (petrol),
			WBR-SWF-F,Front wheel bearing,WHEEL_BEARING,Bearline,650,820,6,G2,Swift; Dzire; Baleno,
			SA-SWF-F,Front shock absorber,SHOCK_ABSORBER,RideSafe,2350,2900,2,H1,Swift 2018-2024; Dzire 2017-2024,
			SA-ACT-F,Front shock absorber,SHOCK_ABSORBER,RideSafe,1050,1350,3,H2,Activa,
			TRE-SWF,Tie rod end,TIE_ROD_END,RideSafe,520,680,5,H3,Swift; Dzire,
			LR-I20,Stabiliser link,STABILISER_LINK,RideSafe,480,620,4,H3,i20 2015-2023,
			EM-SWF-R,Engine mount right,ENGINE_MOUNT,RideSafe,1450,1850,2,H4,Swift 2018-2024,
			CSK-SPL,Chain sprocket kit,CHAIN_SPROCKET,Chainex,780,990,9,D2,Splendor; HF Deluxe; Passion,
			CSK-PUL,Chain sprocket kit,CHAIN_SPROCKET,Chainex,1150,1450,0,D2,Pulsar,
			CC-SPL,Clutch cable,CABLE,Chainex,130,170,25,D3,Splendor; HF Deluxe,
			RAD-SWF,Radiator,RADIATOR,Radiaco,4900,5900,1,J1,Swift 2012-2024 (diesel); Dzire 2012-2024 (diesel),
			WP-BOL,Water pump,WATER_PUMP,Radiaco,2200,2750,2,J1,Bolero; Scorpio,
			""";

	/** What mechanics typically ask, for the weeks of history: car garages, then two-wheeler ones. */
	private static final List<String> CAR_ASKS = List.of("Swift front brake pad", "Dzire 2019 front pads",
			"i20 2017 front brake pad", "Alto 800 clutch plate", "swift clutch plate", "Bolero clutch plate",
			"Alto oil filter", "WagonR air filter", "Swift 2019 air filter", "Creta front brake pad", "Innova air filter",
			"Bolero diesel filter", "Swift front shocker", "Alto self motor", "Bolero dynamo", "Swift fan belt",
			"Dzire front wheel bearing", "Nexon front brake pad", "Scorpio N clutch plate", "Creta cabin filter",
			"Swift tie rod end left", "i20 link rod front", "Bolero oil filter", "Swift spark plug 4 pcs",
			"WagonR front brake pad", "Thar clutch plate");

	private static final List<String> BIKE_ASKS = List.of("Splendor chain sprocket", "Pulsar 150 chain sprocket kit",
			"Activa brake shoe", "Splendor spark plug x2", "Activa front shocker", "Splendor clutch cable",
			"Pulsar front disc pad", "Classic 350 chain sprocket");

	private final AppProperties properties;

	private final Users users;

	private final SignupService signups;

	private final Accounts accounts;

	private final StockCsv stock;

	private final Parts parts;

	private final Mechanics mechanics;

	private final Enquiries enquiries;

	private final EnquiryService service;

	private final QuoteOptions options;

	private final Bookings bookings;

	private final FileStore files;

	private final Messenger messenger;

	private final InboundMessages inbound;

	private final JdbcClient jdbc;

	private final Clock clock;

	private long account;

	private OffsetDateTime now;

	public PartsDemoData(AppProperties properties, Users users, SignupService signups, Accounts accounts, StockCsv stock,
			Parts parts, Mechanics mechanics, Enquiries enquiries, EnquiryService service, QuoteOptions options,
			Bookings bookings, FileStore files, Messenger messenger, InboundMessages inbound, JdbcClient jdbc, Clock clock) {
		this.properties = properties;
		this.users = users;
		this.signups = signups;
		this.accounts = accounts;
		this.stock = stock;
		this.parts = parts;
		this.mechanics = mechanics;
		this.enquiries = enquiries;
		this.service = service;
		this.options = options;
		this.bookings = bookings;
		this.files = files;
		this.messenger = messenger;
		this.inbound = inbound;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		AppProperties.Demo demo = properties.demo();
		if (!demo.enabled() || users.findByEmail(demo.email()).isPresent()) {
			return;
		}
		now = OffsetDateTime.now(clock);
		account = signups.createDemo("Shree Balaji Auto Spares", "Mahesh Agarwal", "+919000090000", demo.email(),
				demo.password());
		ProfileForm profile = ProfileForm.of(accounts.get(account));
		profile.setAddress("Shop 7, Auto Market, Transport Nagar, Jaipur 302003");
		accounts.updateProfile(account, profile);
		accounts.setPlan(account, PartsNavigation.PRO);
		stock.importCsv(account, STOCK.getBytes(java.nio.charset.StandardCharsets.UTF_8));

		List<Mechanic> cars = List.of(mechanic("+919000091001", "Raju Saini", "Raju Motor Garage", "Sanganer"),
				mechanic("+919000091002", "Imran Qureshi", "Al-Noor Car Care", "Ramganj"),
				mechanic("+919000091003", "Sunil Sharma", "Sharma Auto Works", "Malviya Nagar"),
				mechanic("+919000091004", "Gurpreet Singh", "Punjab Diesel Works", "Transport Nagar"),
				mechanic("+919000091006", "Vikas Meena", "Meena Garage", "Vaishali Nagar"));
		List<Mechanic> bikes = List.of(mechanic("+919000091005", "Mohan Lal", "Mohan Two Wheeler Service", "Jhotwara"),
				mechanic("+919000091007", "Arif Khan", "Khan Bike Point", "Sodala"));

		history(cars, bikes);
		today(cars, bikes);
		log.info("Demo parts counter created: sign in as {} / {}", demo.email(), demo.password());
	}

	/** Three weeks of enquiries, answered the way a busy counter does, for the reports. */
	private void history(List<Mechanic> cars, List<Mechanic> bikes) {
		Random random = new Random(42);
		LocalDate today = LocalDate.now(clock);
		for (int daysAgo = 21; daysAgo >= 1; daysAgo--) {
			LocalDate day = today.minusDays(daysAgo);
			int count = 2 + random.nextInt(4);
			for (int i = 0; i < count; i++) {
				boolean bike = random.nextInt(4) == 0;
				Mechanic mechanic = bike ? bikes.get(random.nextInt(bikes.size())) : cars.get(random.nextInt(cars.size()));
				String text = bike ? BIKE_ASKS.get(random.nextInt(BIKE_ASKS.size()))
						: CAR_ASKS.get(random.nextInt(CAR_ASKS.size()));
				OffsetDateTime asked = day.atTime(9 + random.nextInt(10), random.nextInt(60), random.nextInt(60))
					.atZone(clock.getZone())
					.toOffsetDateTime();
				past(mechanic, text, asked, random);
			}
		}
	}

	private void past(Mechanic mechanic, String text, OffsetDateTime asked, Random random) {
		Identification found = TextParser.parse(text);
		long id = enquiries.create(account, mechanic.id(), null, Enquiry.TEXT, null, text, null, found, asked);
		Enquiry enquiry = enquiries.get(account, id);
		List<PartMatcher.Candidate> candidates = service.candidates(enquiry);
		List<Part> sure = candidates.stream().filter((c) -> c.sure() && c.part().inStock()).map(PartMatcher.Candidate::part)
			.limit(2).toList();
		List<Part> inStock = candidates.stream().map(PartMatcher.Candidate::part).filter(Part::inStock).limit(2).toList();
		if (!sure.isEmpty() && found.question() == null) {
			quotePast(enquiry, sure, asked.plusSeconds(4 + random.nextInt(6)), Enquiry.AUTO, random);
		}
		else if (!inStock.isEmpty()) {
			quotePast(enquiry, inStock, asked.plusSeconds(35 + random.nextInt(340)), Enquiry.STAFF, random);
		}
		else if (random.nextInt(3) == 0) {
			enquiries.setStatus(account, id, Enquiry.CLOSED, asked.plusHours(2));
		}
		else {
			OffsetDateTime answered = asked.plusSeconds(40 + random.nextInt(260));
			enquiries.answered(account, id, Enquiry.STAFF, answered);
			enquiries.setStatus(account, id, Enquiry.NOT_AVAILABLE, answered);
		}
	}

	/** A past quote: booked and handed over two times in three, otherwise left to lapse. */
	private void quotePast(Enquiry enquiry, List<Part> offered, OffsetDateTime answered, String by, Random random) {
		options.replace(enquiry.id(), offered.stream().map(service::option).toList(), answered);
		enquiries.answered(account, enquiry.id(), by, answered);
		if (random.nextInt(3) < 2) {
			Part part = offered.getFirst();
			OffsetDateTime booked = answered.plusMinutes(1 + random.nextInt(15));
			long bookingId = bookings.create(account, enquiry.id(), enquiry.mechanicId(), part.id(), part.describe(),
					enquiry.quantity(), part.price(), booked);
			String mode = List.of("CASH", "CASH", "UPI", "UPI", "CREDIT").get(random.nextInt(5));
			bookings.finish(account, bookingId, "HANDED_OVER", mode, booked.plusMinutes(20 + random.nextInt(70)));
			enquiries.setStatus(account, enquiry.id(), Enquiry.BOOKED, booked);
		}
		else {
			enquiries.setStatus(account, enquiry.id(), Enquiry.CLOSED, answered.plusDays(1));
		}
	}

	/** Today's counter, through the same steps as real WhatsApp messages, then dated. */
	private void today(List<Mechanic> cars, List<Mechanic> bikes) {
		Mechanic raju = cars.get(0);
		Mechanic imran = cars.get(1);
		Mechanic sunil = cars.get(2);
		Mechanic gurpreet = cars.get(3);
		Mechanic vikas = cars.get(4);
		Mechanic mohan = bikes.get(0);

		// Sunil asked for two oil filters this morning: quoted automatically, booked and collected.
		OffsetDateTime t = now.minusMinutes(52);
		Enquiry oil = ask(sunil, "Swift 2019 oil filter 2 pcs", null, t);
		book(sunil, oil, 1, t.plusMinutes(3));
		jdbc.sql("UPDATE booking SET status = 'HANDED_OVER', payment_mode = 'UPI', done_at = ? WHERE enquiry_id = ?")
			.params(t.plusMinutes(31), oil.id())
			.update();

		// Raju sent the brake pad box: both pads that fit were quoted in seconds and he booked the first.
		t = now.minusMinutes(9);
		StoredFile box = photo(PartSamples.box("ProStop", "Disc brake pad set", "Front axle", "BP-SWF-PS",
				"Maruti Swift / Dzire 2018–2024", new Color(176, 30, 40)), "brake-box.jpg");
		Enquiry pads = ask(raju, "Swift ka front brake pad chahiye", box, t);
		book(raju, pads, 1, t.plusMinutes(2).plusSeconds(10));

		// Mohan's Pulsar chain kit was out of stock; the counter offered it for tomorrow.
		t = now.minusMinutes(26);
		Enquiry chain = ask(mohan, "Pulsar 150 chain sprocket kit", null, t);
		service.notAvailable(account, chain.id(), "We can get it by tomorrow 11 am, ₹1,150.");
		dateLastMessage(chain.id(), t.plusMinutes(2).plusSeconds(40));
		jdbc.sql("UPDATE enquiry SET first_reply_at = ?, closed_at = ? WHERE id = ?")
			.params(t.plusMinutes(2).plusSeconds(40), t.plusMinutes(2).plusSeconds(40), chain.id())
			.update();

		// Imran did not say front or rear; the first reply asked, his answer made it sure and it was quoted.
		t = now.minusMinutes(6);
		Enquiry i20 = ask(imran, "i20 brake pad hai kya?", null, t);
		OffsetDateTime front = t.plusMinutes(1).plusSeconds(5);
		received(imran, "front", null, front);
		service.addDetail(account, i20, "front");
		dateLastMessage(i20.id(), front.plusSeconds(4));
		jdbc.sql("UPDATE enquiry SET first_reply_at = ? WHERE id = ?").params(front.plusSeconds(4), i20.id()).update();

		// Still at the counter: a Bolero clutch plate (only the kit is in stock) and a photo with no words.
		t = now.minusSeconds(95);
		StoredFile tag = photo(PartSamples.tag("Bolero 2016", "Clutch plate", "Gurpreet - Punjab Diesel"), "clutch-tag.jpg");
		ask(gurpreet, "Bolero clutch plate, urgent", tag, t);
		t = now.minusSeconds(40);
		StoredFile filter = photo(PartSamples.box("Filtra", "Oil filter", "Spin-on, petrol", "OF-MS-110",
				"Maruti Alto / WagonR / Swift / Dzire", new Color(21, 94, 117)), "filter.jpg");
		ask(vikas, null, filter, t);
	}

	/** An enquiry as it arrives on WhatsApp: the message, then the counter's automatic answer, dated. */
	private Enquiry ask(Mechanic mechanic, String text, StoredFile photo, OffsetDateTime at) {
		long inboundId = received(mechanic, text, photo, at);
		Identification found = photo != null && text == null
				? Identification.nothing("Photo: look at it at the counter (automatic reading is off).") : TextParser.parse(text);
		Enquiry enquiry = service.open(account, mechanic, inboundId, photo != null ? Enquiry.PHOTO : Enquiry.TEXT, photo, text,
				null, found, at);
		dateMessages(enquiry.id(), at.plusSeconds(4));
		if (enquiry.firstReplyAt() != null) {
			jdbc.sql("UPDATE enquiry SET first_reply_at = ? WHERE id = ?").params(at.plusSeconds(4), enquiry.id()).update();
		}
		return enquiries.get(account, enquiry.id());
	}

	/** The mechanic's "1": booked through the service, with the confirmation he was sent. */
	private void book(Mechanic mechanic, Enquiry enquiry, int option, OffsetDateTime at) {
		received(mechanic, String.valueOf(option), null, at);
		String reply = service.book(account, mechanic, enquiries.get(account, enquiry.id()), option);
		messenger.send(Outgoing.whatsApp(account, mechanic.phone(), reply).about("enquiry", enquiry.id()));
		dateLastMessage(enquiry.id(), at.plusSeconds(2));
		jdbc.sql("UPDATE booking SET created_at = ? WHERE enquiry_id = ?").params(at.plusSeconds(1), enquiry.id()).update();
		jdbc.sql("UPDATE enquiry SET closed_at = ? WHERE id = ?").params(at.plusSeconds(1), enquiry.id()).update();
	}

	private Mechanic mechanic(String phone, String name, String garage, String area) {
		Mechanic mechanic = mechanics.findOrCreate(account, phone, name, now.minusDays(60));
		mechanics.update(account, mechanic.id(), name, garage, area, null);
		return mechanics.get(account, mechanic.id());
	}

	private StoredFile photo(byte[] jpeg, String name) {
		return files.save(account, jpeg, "image/jpeg", name);
	}

	private long received(Mechanic mechanic, String text, StoredFile photo, OffsetDateTime at) {
		long id = inbound.insertIfNew(new InboundMessages.NewInbound(account, "demo-" + UUID.randomUUID(), mechanic.phone(),
				SimulatorController.PLATFORM_NUMBER, mechanic.name(), photo != null ? "image" : "text", text, null,
				photo != null ? "image/jpeg" : null, photo != null ? photo.originalName() : null,
				photo != null ? photo.id() : null, null, at)).orElseThrow();
		inbound.finish(id, InboundMessage.PROCESSED, null, at);
		return id;
	}

	/** Every message so far about the enquiry, a few seconds apart from the given time. */
	private void dateMessages(long enquiryId, OffsetDateTime from) {
		List<Long> ids = messages(enquiryId);
		for (int i = 0; i < ids.size(); i++) {
			OffsetDateTime at = from.plusSeconds(2L * i);
			jdbc.sql("UPDATE outbound_message SET created_at = ?, status_updated_at = ? WHERE id = ?")
				.params(at, at, ids.get(i))
				.update();
		}
	}

	private void dateLastMessage(long enquiryId, OffsetDateTime at) {
		List<Long> ids = messages(enquiryId);
		if (!ids.isEmpty()) {
			jdbc.sql("UPDATE outbound_message SET created_at = ?, status_updated_at = ? WHERE id = ?")
				.params(at, at, ids.getLast())
				.update();
		}
	}

	private List<Long> messages(long enquiryId) {
		return new ArrayList<>(jdbc.sql("""
				SELECT id FROM outbound_message WHERE related_type = 'enquiry' AND related_id = ? ORDER BY id
				""").param(enquiryId).query(Long.class).list());
	}

}
