package com.liabletech.materials.whatsapp;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.liabletech.materials.delivery.CountReply;
import com.liabletech.materials.delivery.Deliveries;
import com.liabletech.materials.delivery.Delivery;
import com.liabletech.materials.delivery.DeliveryLine;
import com.liabletech.materials.delivery.DeliveryService;
import com.liabletech.materials.leakage.Flag;
import com.liabletech.materials.site.Site;
import com.liabletech.materials.site.SiteMember;
import com.liabletech.materials.site.Sites;
import com.liabletech.materials.supplier.Supplier;
import com.liabletech.materials.supplier.Suppliers;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.whatsapp.InboundContext;
import com.liabletech.platform.whatsapp.InboundHandler;
import com.liabletech.platform.whatsapp.InboundMessage;
import com.liabletech.platform.whatsapp.InboundRouter;
import com.liabletech.platform.whatsapp.SimulatorDirectory;

/**
 * The supervisor's side, all on WhatsApp: photograph the slip at the gate, then answer "OK" or
 * "1=195" with what was counted.
 */
@Component
public class MaterialsWhatsApp implements InboundRouter, InboundHandler, SimulatorDirectory {

	static final String HELP = """
			Send a photo of every delivery challan when material arrives at site. We will list what the slip says; \
			reply OK if everything arrived, or the line number and what you counted, e.g. 1=195.
			You can also send supplier bills here.""";

	private final Sites sites;

	private final Deliveries deliveries;

	private final DeliveryService deliveryService;

	private final Suppliers suppliers;

	private final Clock clock;

	public MaterialsWhatsApp(Sites sites, Deliveries deliveries, DeliveryService deliveryService, Suppliers suppliers,
			Clock clock) {
		this.sites = sites;
		this.deliveries = deliveries;
		this.deliveryService = deliveryService;
		this.suppliers = suppliers;
		this.clock = clock;
	}

	@Override
	public Optional<Long> accountFor(String fromPhone) {
		return sites.memberByPhone(fromPhone).filter(SiteMember::active).map(SiteMember::accountId);
	}

	@Override
	public void handle(InboundContext context) {
		InboundMessage message = context.message();
		Optional<SiteMember> member = sites.memberByPhone(context.from())
			.filter((m) -> m.accountId().equals(context.accountId()));
		if (member.isEmpty()) {
			context.reply("This number is not set up for any site. Ask the office to add you as a supervisor.");
			return;
		}
		SiteMember supervisor = member.get();
		if (message.isPhotoOrPdf()) {
			Optional<StoredFile> file = context.file();
			if (file.isEmpty()) {
				context.reply("We could not open that file. Please send the challan as a photo.");
				return;
			}
			DeliveryService.Received received = deliveryService.receive(context.accountId(), supervisor.siteId(), supervisor,
					file.get(), message.text(), "WHATSAPP");
			context.reply(replyFor(context.accountId(), received), received.isBill() ? "bill" : "delivery",
					received.isBill() ? received.billId() : received.delivery().id());
			return;
		}
		if (message.isAudio()) {
			context.reply("Voice notes are not read here. " + HELP);
			return;
		}
		Optional<Delivery> awaiting = deliveries.latestAwaitingCount(context.accountId(), supervisor.id());
		if (awaiting.isPresent()) {
			List<DeliveryLine> lines = deliveries.lines(awaiting.get().id());
			Optional<CountReply> reply = CountReply.parse(message.text(), lines.size());
			if (reply.isPresent()) {
				List<Flag> shortages = deliveryService.applyCount(context.accountId(), awaiting.get().id(), reply.get());
				context.reply(DeliveryService.countRecorded(shortages), "delivery", awaiting.get().id());
				return;
			}
			String text = message.text().toLowerCase(Locale.ROOT);
			if (text.contains("short") || text.contains("kam") || text.equals("no")) {
				context.reply("Please reply with the line number and what you counted, e.g. 1=195. Use OK if all arrived.",
						"delivery", awaiting.get().id());
				return;
			}
		}
		context.reply("Namaste " + supervisor.name().split(" ")[0] + "! " + HELP);
	}

	@Override
	public String replyToUnknownSender(InboundMessage message) {
		return "This number is not set up for any site. Ask the office to add you as a supervisor in Material Tracker.";
	}

	private String replyFor(long accountId, DeliveryService.Received received) {
		if (received.isBill()) {
			return "🧾 Bill received" + (received.reading().documentNumber() == null ? ""
					: " (no. " + received.reading().documentNumber() + ")")
					+ ". The office will check it against the deliveries counted at site.";
		}
		Delivery delivery = received.delivery();
		if (Delivery.AWAITING_COUNT.equals(delivery.status())) {
			String supplierName = suppliers.find(accountId, delivery.supplierId())
				.map(Supplier::name)
				.orElse(delivery.supplierNameRead());
			return deliveryService.askForCount(delivery, deliveries.lines(delivery.id()), supplierName);
		}
		if (Delivery.MANUAL.equals(delivery.readingStatus())) {
			return "📦 Slip received. The office will enter the quantities. Thank you!";
		}
		return "📦 Slip received, but we could not read it clearly. Please send a sharper photo of the whole challan, "
				+ "or the office will enter it.";
	}

	/** Supervisors with a slip waiting for their count come first: that is the chat to try. */
	@Override
	public List<Contact> contacts(long accountId) {
		List<SiteMember> members = new ArrayList<>(sites.members(accountId));
		members.sort(Comparator.comparing((SiteMember m) -> deliveries.latestAwaitingCount(accountId, m.id()).isEmpty()));
		List<Contact> contacts = new ArrayList<>();
		for (SiteMember member : members) {
			String site = sites.find(accountId, member.siteId()).map(Site::name).orElse("no site");
			contacts.add(new Contact(member.name(), member.phone(),
					(SiteMember.STOREKEEPER.equals(member.role()) ? "Storekeeper" : "Supervisor") + " · " + site));
		}
		return contacts;
	}

	@Override
	public List<Sample> samples(long accountId) {
		List<Supplier> all = suppliers.all(accountId);
		String supplier = all.isEmpty() ? "Shree Balaji Cement Traders" : all.getFirst().name();
		String gstin = all.isEmpty() || all.getFirst().gstin() == null ? "27AAAFB1234C1Z1" : all.getFirst().gstin();
		String site = sites.all(accountId).stream().findFirst().map(Site::name).orElse("Site");
		LocalDate today = LocalDate.now(clock);
		String challan = String.valueOf(4400 + (int) (System.nanoTime() % 500));
		return List.of(
				new Sample("challan", "Delivery challan (cement + steel)", "",
						() -> new SampleFile(SlipSamples.challan(supplier, gstin, challan, today, "MH12QW4521", site,
								List.of(new SlipSamples.Item("UltraTech OPC 53 Grade Cement", "200", "Bags", ""),
										new SlipSamples.Item("TMT Fe500D 12 mm", "1.250", "MT", ""))),
								"image/jpeg", "challan-" + challan + ".jpg")),
				new Sample("bill", "Supplier bill", "",
						() -> new SampleFile(SlipSamples.invoice(supplier, gstin, "INV/" + challan, today, "Builder", site,
								List.of(challan),
								List.of(new SlipSamples.Item("UltraTech OPC 53 Grade Cement", "200", "Bags", "405"),
										new SlipSamples.Item("TMT Fe500D 12 mm", "1.250", "MT", "62000")),
								"158500.00", "35740.00", "194240.00"), "image/jpeg", "bill-" + challan + ".jpg")));
	}

}
