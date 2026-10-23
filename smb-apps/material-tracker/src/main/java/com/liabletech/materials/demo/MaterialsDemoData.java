package com.liabletech.materials.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.liabletech.materials.bill.BillService;
import com.liabletech.materials.bill.Bills;
import com.liabletech.materials.catalog.Material;
import com.liabletech.materials.catalog.Materials;
import com.liabletech.materials.catalog.Unit;
import com.liabletech.materials.delivery.CountReply;
import com.liabletech.materials.delivery.Deliveries;
import com.liabletech.materials.delivery.DeliveryService;
import com.liabletech.materials.leakage.ReconcileService;
import com.liabletech.materials.reading.DocumentReader;
import com.liabletech.materials.reading.MaterialDocReading;
import com.liabletech.materials.site.SiteMember;
import com.liabletech.materials.site.Sites;
import com.liabletech.materials.supplier.Suppliers;
import com.liabletech.materials.whatsapp.SlipSamples;
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
 * A demo builder with two sites and three suppliers: a short cement delivery, a bill at more than
 * the agreed rate, steel billed above its weight tolerance, a challan billed twice, one dispute
 * already recovered, and a slip still waiting for the supervisor's count on WhatsApp.
 */
@Component
public class MaterialsDemoData implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(MaterialsDemoData.class);

	private final AppProperties properties;

	private final Users users;

	private final SignupService signups;

	private final Accounts accounts;

	private final Sites sites;

	private final Suppliers suppliers;

	private final Materials materials;

	private final DeliveryService deliveryService;

	private final Deliveries deliveries;

	private final BillService billService;

	private final ReconcileService reconcile;

	private final FileStore files;

	private final Messenger messenger;

	private final InboundMessages inbound;

	private final Bills bills;

	private final JdbcClient jdbc;

	private final Clock clock;

	public MaterialsDemoData(AppProperties properties, Users users, SignupService signups, Accounts accounts, Sites sites,
			Suppliers suppliers, Materials materials, DeliveryService deliveryService, Deliveries deliveries,
			BillService billService, ReconcileService reconcile, FileStore files, Messenger messenger,
			InboundMessages inbound, Bills bills, JdbcClient jdbc, Clock clock) {
		this.properties = properties;
		this.users = users;
		this.signups = signups;
		this.accounts = accounts;
		this.sites = sites;
		this.suppliers = suppliers;
		this.materials = materials;
		this.deliveryService = deliveryService;
		this.deliveries = deliveries;
		this.billService = billService;
		this.reconcile = reconcile;
		this.files = files;
		this.messenger = messenger;
		this.inbound = inbound;
		this.bills = bills;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		AppProperties.Demo demo = properties.demo();
		if (!demo.enabled() || users.findByEmail(demo.email()).isPresent()) {
			return;
		}
		LocalDate today = LocalDate.now(clock);
		long account = signups.createDemo("Shubh Constructions", "Rahul Deshmukh", "+919000030000", demo.email(),
				demo.password());
		ProfileForm profile = ProfileForm.of(accounts.get(account));
		profile.setAddress("Office 7, Kalyani Nagar, Pune 411006");
		profile.setGstin("27AAKFS9876K1Z3");
		accounts.updateProfile(account, profile);

		long residency = sites.create(account, "Shubh Residency, Wagholi", "Wagholi, Pune");
		long bungalows = sites.create(account, "Green Park Bungalows, Baner", "Baner, Pune");
		SiteMember santosh = sites.member(account, sites.addMember(account, residency, "Santosh Patil", "+919000030001",
				SiteMember.SUPERVISOR));
		SiteMember ajay = sites.member(account, sites.addMember(account, bungalows, "Ajay Kumar", "+919000030002",
				SiteMember.SUPERVISOR));

		long balaji = suppliers.create(account, "Shree Balaji Cement Traders", "+919000040001",
				"sales@balaji-cement.example", "27AAAFB1234C1Z1");
		long maruti = suppliers.create(account, "Maruti Steel & Hardware", "+919000040002", "accounts@maruti-steel.example",
				"27AAACM5678D1Z2");
		long mula = suppliers.create(account, "Mula River Sand Suppliers", "+919000040003", null, "27AAGFM4455E1Z7");

		Map<String, Material> byName = new java.util.HashMap<>();
		materials.all(account).forEach((m) -> byName.put(m.name(), m));
		LocalDate from = today.minusMonths(3);
		suppliers.addRate(account, balaji, byName.get("Cement OPC 53").id(), BigDecimal.valueOf(390), Unit.BAG, from, null);
		suppliers.addRate(account, balaji, byName.get("Cement PPC").id(), BigDecimal.valueOf(370), Unit.BAG, from, null);
		suppliers.addRate(account, maruti, byName.get("TMT bar 12 mm").id(), BigDecimal.valueOf(60), Unit.KG, from, null);
		suppliers.addRate(account, maruti, byName.get("TMT bar 8 mm").id(), BigDecimal.valueOf(62), Unit.KG, from, null);
		suppliers.addRate(account, mula, byName.get("M-sand").id(), BigDecimal.valueOf(55), Unit.CFT, from, null);
		suppliers.addRate(account, mula, byName.get("Aggregate 20 mm").id(), BigDecimal.valueOf(42), Unit.CFT, from, null);

		String balajiName = "Shree Balaji Cement Traders";
		// Cement counted 5 bags short at the gate, then billed in full and above the agreed rate.
		long d4401 = delivery(account, residency, santosh, balajiName, "27AAAFB1234C1Z1", "4401", today.minusDays(12),
				"MH12QW4521", "Shubh Residency, Wagholi", List.of(line("UltraTech OPC 53 Grade Cement", "200", "Bags")));
		deliveryService.applyCount(account, d4401, new CountReply(false, Map.of(1, BigDecimal.valueOf(195))));
		long b1 = bill(account, residency, balajiName, "27AAAFB1234C1Z1", "SBCT/2026/1187", today.minusDays(9),
				"Shubh Residency, Wagholi", List.of("4401"), List.of(item("UltraTech OPC 53 Grade Cement", "200", "Bags", "405", "4401")),
				"81000", "22680", "103680");
		reconcile.check(account, b1);
		billService.raiseWithSupplier(account, b1);

		// Steel billed 30 kg above the 2% rolling tolerance, with the line amount padded too.
		long dM778 = delivery(account, residency, santosh, "Maruti Steel & Hardware", "27AAACM5678D1Z2", "M-778",
				today.minusDays(10), "MH14ZX7781", "Shubh Residency, Wagholi", List.of(line("TMT Fe500D 12 mm", "1.250", "MT")));
		deliveryService.applyCount(account, dM778, new CountReply(true, Map.of()));
		long b2 = bill(account, residency, "Maruti Steel & Hardware", "27AAACM5678D1Z2", "MSH-2026-0457", today.minusDays(6),
				"Shubh Residency, Wagholi", List.of("M-778"),
				List.of(new DocumentReader.Line("TMT Fe500D 12 mm", new BigDecimal("1.280"), "MT", BigDecimal.valueOf(60000),
						new BigDecimal("77800"), "M-778")),
				"77800", "14004", "91804");
		reconcile.check(account, b2);

		// Sand measured short in the truck at Baner, not billed yet.
		long d2231 = delivery(account, bungalows, ajay, "Mula River Sand Suppliers", "27AAGFM4455E1Z7", "2231",
				today.minusDays(8), "MH12LK2231", "Green Park Bungalows, Baner", List.of(line("M-Sand (plaster)", "2", "Brass")));
		deliveryService.applyCount(account, d2231, new CountReply(false, Map.of(1, new BigDecimal("1.8"))));

		// PPC delivered and billed fine, but the bill also charges challan 4401 a second time.
		long d4410 = delivery(account, bungalows, ajay, balajiName, "27AAAFB1234C1Z1", "4410", today.minusDays(5), "MH12QW4521",
				"Green Park Bungalows, Baner", List.of(line("Ambuja PPC Cement", "100", "Bags")));
		deliveryService.applyCount(account, d4410, new CountReply(true, Map.of()));
		long b3 = bill(account, bungalows, balajiName, "27AAAFB1234C1Z1", "SBCT/2026/1242", today.minusDays(3),
				"Green Park Bungalows, Baner", List.of("4410", "4401"),
				List.of(item("Ambuja PPC Cement", "100", "Bags", "370", "4410"),
						item("UltraTech OPC 53 Grade Cement", "200", "Bags", "390", "4401")),
				"115000", "32200", "147200");
		reconcile.check(account, b3);

		// An earlier aggregate bill above the agreed rate, already credited by the supplier.
		long d1904 = delivery(account, bungalows, ajay, "Mula River Sand Suppliers", "27AAGFM4455E1Z7", "1904",
				today.minusDays(24), "MH12LK2231", "Green Park Bungalows, Baner", List.of(line("20 mm metal", "300", "Cft")));
		deliveryService.applyCount(account, d1904, new CountReply(true, Map.of()));
		long b4 = bill(account, bungalows, "Mula River Sand Suppliers", "27AAGFM4455E1Z7", "MRS/88", today.minusDays(20),
				"Green Park Bungalows, Baner", List.of("1904"), List.of(item("20 mm metal", "300", "Cft", "45", "1904")),
				"13500", "675", "14175");
		reconcile.check(account, b4);
		billService.raiseWithSupplier(account, b4);
		billService.markRecovered(account, b4, "Credit note MRS/CN/12 for ₹900 received");

		// Today's slip, still waiting for Santosh to confirm the count (try replying "1=148" in the simulator).
		StoredFile photo = challanPhoto(account, balajiName, "27AAAFB1234C1Z1", "4415", today, "MH12QW4521",
				"Shubh Residency, Wagholi", List.of(line("UltraTech OPC 53 Grade Cement", "150", "Bags")));
		long inboundId = inbound
			.insertIfNew(new InboundMessages.NewInbound(account, "demo-" + UUID.randomUUID(), santosh.phone(),
					SimulatorController.PLATFORM_NUMBER, santosh.name(), "image", null, null, "image/jpeg",
					photo.originalName(), photo.id(), null, OffsetDateTime.now(clock).minusMinutes(2)))
			.orElseThrow();
		inbound.finish(inboundId, InboundMessage.PROCESSED, null, OffsetDateTime.now(clock).minusMinutes(2));
		DeliveryService.Received pending = deliveryService.record(account, residency, santosh, photo, null, "WHATSAPP",
				reading(balajiName, "27AAAFB1234C1Z1", "4415", today, "MH12QW4521",
						List.of(line("UltraTech OPC 53 Grade Cement", "150", "Bags"))));
		messenger.send(Outgoing.whatsApp(account, santosh.phone(),
				deliveryService.askForCount(pending.delivery(), deliveries.lines(pending.delivery().id()), balajiName))
			.about("delivery", pending.delivery().id()));
		backdate(account, today);
		log.info("Demo builder created: sign in as {} / {}", demo.email(), demo.password());
	}

	/**
	 * The demo is created in one go. Give its slips, bills, findings and messages the times they
	 * would have had: a slip on its challan date, a bill the day after its date.
	 */
	private void backdate(long account, LocalDate today) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		for (Deliveries.DeliveryRow row : deliveries.list(account, null, null, 100)) {
			if (row.challanDate() == null || !row.challanDate().isBefore(today)) {
				continue;
			}
			OffsetDateTime received = row.challanDate().atTime(11, 15).atZone(clock.getZone()).toOffsetDateTime();
			OffsetDateTime counted = received.plusMinutes(35);
			jdbc.sql("UPDATE delivery SET received_at = ?, confirmed_at = ? WHERE id = ?")
				.params(received, counted, row.id())
				.update();
			jdbc.sql("UPDATE leakage_flag SET created_at = ? WHERE delivery_id = ? AND bill_id IS NULL")
				.params(counted, row.id())
				.update();
		}
		for (Bills.BillRow row : bills.list(account, null, null, 100)) {
			OffsetDateTime created = min(row.billDate().plusDays(1).atTime(12, 30).atZone(clock.getZone()).toOffsetDateTime(),
					now.minusHours(3));
			OffsetDateTime checked = created.plusMinutes(50);
			OffsetDateTime settled = min(checked.plusDays(2), now.minusHours(1));
			jdbc.sql("UPDATE supplier_bill SET created_at = ?, checked_at = ? WHERE id = ?")
				.params(created, checked, row.id())
				.update();
			jdbc.sql("""
					UPDATE leakage_flag SET created_at = ?,
					    resolved_at = CASE WHEN status IN ('OPEN', 'RAISED') THEN NULL ELSE ? END
					WHERE bill_id = ?
					""").params(checked, settled, row.id()).update();
			jdbc.sql("""
					UPDATE outbound_message SET created_at = ?
					WHERE account_id = ? AND related_type = 'bill' AND related_id = ?
					""").params(checked.plusMinutes(10), account, row.id()).update();
		}
	}

	private static OffsetDateTime min(OffsetDateTime a, OffsetDateTime b) {
		return a.isBefore(b) ? a : b;
	}

	private long delivery(long account, long siteId, SiteMember member, String supplier, String gstin, String challan,
			LocalDate date, String vehicle, String siteName, List<DocumentReader.Line> lines) {
		StoredFile photo = challanPhoto(account, supplier, gstin, challan, date, vehicle, siteName, lines);
		return deliveryService
			.record(account, siteId, member, photo, null, "WHATSAPP", reading(supplier, gstin, challan, date, vehicle, lines))
			.delivery()
			.id();
	}

	private StoredFile challanPhoto(long account, String supplier, String gstin, String challan, LocalDate date,
			String vehicle, String siteName, List<DocumentReader.Line> lines) {
		List<SlipSamples.Item> items = lines.stream()
			.map((l) -> new SlipSamples.Item(l.description(), l.quantity().stripTrailingZeros().toPlainString(), l.unit(), ""))
			.toList();
		return files.save(account, SlipSamples.challan(supplier, gstin, challan, date, vehicle, siteName, items),
				"image/jpeg", "challan-" + challan + ".jpg");
	}

	private long bill(long account, long siteId, String supplier, String gstin, String number, LocalDate date,
			String siteName, List<String> challans, List<DocumentReader.Line> lines, String taxable, String gst,
			String total) {
		List<SlipSamples.Item> items = new ArrayList<>();
		for (DocumentReader.Line l : lines) {
			items.add(new SlipSamples.Item(l.description(), l.quantity().toPlainString(), l.unit(), l.rate().toPlainString()));
		}
		StoredFile photo = files.save(account,
				SlipSamples.invoice(supplier, gstin, number, date, "Shubh Constructions", siteName, challans, items, taxable, gst,
						total),
				"image/jpeg", "bill-" + number.replace('/', '-') + ".jpg");
		DocumentReader.Reading reading = new DocumentReader.Reading(true, null,
				MaterialDocReading.DocumentType.TAX_INVOICE, supplier, gstin, number, date, null, lines, challans,
				new BigDecimal(taxable), new BigDecimal(gst), new BigDecimal(total), new BigDecimal("0.92"));
		long id = billService.createFromReading(account, siteId, photo, reading, "UPLOAD");
		return id;
	}

	private static DocumentReader.Reading reading(String supplier, String gstin, String challan, LocalDate date,
			String vehicle, List<DocumentReader.Line> lines) {
		return new DocumentReader.Reading(true, null, MaterialDocReading.DocumentType.DELIVERY_CHALLAN, supplier, gstin,
				challan, date, vehicle, lines, List.of(), null, null, null, new BigDecimal("0.9"));
	}

	private static DocumentReader.Line line(String description, String quantity, String unit) {
		return new DocumentReader.Line(description, new BigDecimal(quantity), unit, null, null, null);
	}

	private static DocumentReader.Line item(String description, String quantity, String unit, String rate,
			String challan) {
		BigDecimal qty = new BigDecimal(quantity);
		BigDecimal price = new BigDecimal(rate);
		return new DocumentReader.Line(description, qty, unit, price, qty.multiply(price), challan);
	}

}
