package com.liabletech.freight.whatsapp;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.liabletech.freight.fleet.Driver;
import com.liabletech.freight.fleet.Fleet;
import com.liabletech.freight.fleet.Vehicle;
import com.liabletech.freight.party.Parties;
import com.liabletech.freight.pod.Pod;
import com.liabletech.freight.pod.PodService;
import com.liabletech.freight.pod.Pods;
import com.liabletech.freight.trip.Trip;
import com.liabletech.freight.trip.Trips;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.whatsapp.InboundContext;
import com.liabletech.platform.whatsapp.InboundHandler;
import com.liabletech.platform.whatsapp.InboundMessage;
import com.liabletech.platform.whatsapp.InboundRouter;
import com.liabletech.platform.whatsapp.SimulatorDirectory;

/**
 * What drivers can do on WhatsApp: send the signed POD photo (or forward a scan), and type
 * "status" to see what the office has received from them.
 */
@Component
public class FreightWhatsApp implements InboundRouter, InboundHandler, SimulatorDirectory {

	static final String HELP = """
			Send a clear photo of the signed and stamped delivery receipt (POD / LR copy) after unloading. \
			You can add the LR number as the caption.
			Type STATUS to see the PODs received from you.""";

	private final Fleet fleet;

	private final PodService podService;

	private final Pods pods;

	private final Trips trips;

	private final Parties parties;

	private final Clock clock;

	public FreightWhatsApp(Fleet fleet, PodService podService, Pods pods, Trips trips, Parties parties, Clock clock) {
		this.fleet = fleet;
		this.podService = podService;
		this.pods = pods;
		this.trips = trips;
		this.parties = parties;
		this.clock = clock;
	}

	@Override
	public Optional<Long> accountFor(String fromPhone) {
		return fleet.driverByPhone(fromPhone).filter(Driver::active).map(Driver::accountId);
	}

	@Override
	public void handle(InboundContext context) {
		InboundMessage message = context.message();
		Driver driver = fleet.driverByPhone(context.from())
			.filter((d) -> d.accountId().equals(context.accountId()))
			.orElse(null);
		if (message.isPhotoOrPdf()) {
			Optional<StoredFile> file = context.file();
			if (file.isEmpty()) {
				context.reply("We could not open that file. Please send the POD as a photo.");
				return;
			}
			PodService.Received received = podService.receive(context.accountId(), file.get(), driver, "WHATSAPP",
					message.text());
			Trip trip = trips.find(context.accountId(), received.pod().tripId()).orElse(null);
			context.reply(PodService.replyFor(received, driver, trip), "pod", received.pod().id());
			return;
		}
		String text = message.text().toLowerCase(Locale.ROOT);
		if (driver != null && text.startsWith("status")) {
			context.reply(status(context.accountId(), driver));
			return;
		}
		if (message.isAudio()) {
			context.reply("Voice messages are not read here. " + HELP);
			return;
		}
		context.reply((driver == null ? "Namaste! " : "Namaste " + driver.name().split(" ")[0] + "! ") + HELP);
	}

	@Override
	public String replyToUnknownSender(InboundMessage message) {
		return "This number is not registered as a driver. Please ask your fleet owner to add your mobile number in "
				+ "Freight Chaser.";
	}

	private String status(long accountId, Driver driver) {
		List<Pod> recent = pods.recentFromDriver(accountId, driver.id(), 5);
		if (recent.isEmpty()) {
			return "No PODs received from you yet. " + HELP;
		}
		StringBuilder reply = new StringBuilder("Your last PODs:");
		for (Pod pod : recent) {
			reply.append("\n• ")
				.append(pod.lrNumber() == null ? "LR not read" : "LR " + pod.lrNumber())
				.append(": ")
				.append(switch (pod.reviewStatus()) {
					case Pod.CONFIRMED -> "received ✅";
					case Pod.REJECTED -> "not accepted, please resend ❌";
					default -> "with the office for checking";
				});
		}
		return reply.toString();
	}

	@Override
	public List<Contact> contacts(long accountId) {
		List<Contact> contacts = new ArrayList<>();
		for (Driver driver : fleet.drivers(accountId)) {
			String vehicle = fleet.vehicle(accountId, driver.vehicleId()).map(Vehicle::registration).orElse("no truck set");
			contacts.add(new Contact(driver.name(), driver.phone(), "Driver · " + vehicle + (driver.active() ? "" : " · inactive")));
		}
		return contacts;
	}

	/** A sample POD for a trip that is still on the road, so sending it completes that trip. */
	@Override
	public List<Sample> samples(long accountId) {
		Optional<Trips.TripRow> inTransit = trips.list(accountId, Trip.IN_TRANSIT, 1).stream().findFirst();
		String transporter = parties.all(accountId).stream().findFirst().map((p) -> p.name()).orElse("Shree Transport");
		if (inTransit.isPresent()) {
			Trip trip = trips.get(accountId, inTransit.get().id());
			String vehicle = fleet.vehicle(accountId, trip.vehicleId()).map(Vehicle::registration).orElse("MH12AB1234");
			LocalDate lrDate = trip.lrDate() != null ? trip.lrDate() : LocalDate.now(clock).minusDays(3);
			return List.of(new Sample("pod", "Signed POD for LR " + trip.lrNumber(), "LR " + trip.lrNumber(),
					() -> new SampleFile(PodSampleImage.render(transporter, trip.lrNumber(), lrDate, LocalDate.now(clock),
							vehicle, nz(trip.origin(), "Pune"), nz(trip.destination(), "Nagpur"), "Deccan Agro Foods",
							nz(trip.consignee(), "Vidarbha Traders"), nz(trip.goods(), "Rice"), 240,
							"Recd 240 bags OK"), "image/jpeg", "pod-" + trip.lrNumber() + ".jpg")));
		}
		return List.of(new Sample("pod", "Signed POD (new LR)", "", () -> new SampleFile(PodSampleImage.render(transporter,
				"7781", LocalDate.now(clock).minusDays(2), LocalDate.now(clock), "MH14CD5678", "Nashik", "Surat",
				"Green Valley Exports", "Patel Cold Storage", "Onions", 300, "Recd 298 bags, 2 short"), "image/jpeg",
				"pod-7781.jpg")));
	}

	private static String nz(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

}
