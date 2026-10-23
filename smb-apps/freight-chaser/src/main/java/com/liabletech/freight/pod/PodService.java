package com.liabletech.freight.pod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liabletech.freight.fleet.Driver;
import com.liabletech.freight.fleet.Fleet;
import com.liabletech.freight.fleet.Vehicle;
import com.liabletech.freight.party.Parties;
import com.liabletech.freight.trip.Trip;
import com.liabletech.freight.trip.TripForm;
import com.liabletech.freight.trip.Trips;
import com.liabletech.platform.ai.AiResult;
import com.liabletech.platform.ai.DocumentAi;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.text.Lenient;

/**
 * Turns a photo of a signed lorry receipt into a delivered trip. The photo is read, matched to a
 * trip by its LR number, and either confirmed straight away (clear photo, stamp or signature
 * visible, one matching trip) or left for the owner to check.
 */
@Service
public class PodService {

	/** At or above this, a stamped POD that matches exactly one trip needs no human check. */
	static final double AUTO_CONFIRM_CONFIDENCE = 0.8;

	/** Below this the photo is treated as unreadable and the driver is asked for a better one. */
	static final double READABLE_CONFIDENCE = 0.5;

	private static final Pattern LR_IN_CAPTION = Pattern
		.compile("(?i)\\b(?:lr|gr|cn|c/n|bilty|builty)\\s*(?:no\\.?|number|#|:)?\\s*[:.-]?\\s*([A-Z0-9][A-Z0-9/-]{1,20})");

	private final Pods pods;

	private final Trips trips;

	private final Fleet fleet;

	private final Parties parties;

	private final DocumentAi ai;

	private final Clock clock;

	public PodService(Pods pods, Trips trips, Fleet fleet, Parties parties, DocumentAi ai, Clock clock) {
		this.pods = pods;
		this.trips = trips;
		this.fleet = fleet;
		this.parties = parties;
		this.ai = ai;
		this.clock = clock;
	}

	/**
	 * Stores, reads and matches a POD. Not transactional on purpose: reading can take several
	 * seconds and must not hold a database connection.
	 */
	public Received receive(long accountId, StoredFile file, Driver driver, String source, String caption) {
		long podId = pods.create(accountId, file.id(), driver == null ? null : driver.id(), source, caption,
				OffsetDateTime.now(clock));
		read(accountId, podId, file, caption);
		Outcome outcome = match(accountId, pods.get(accountId, podId), driver);
		return new Received(pods.get(accountId, podId), outcome);
	}

	/**
	 * Records a POD whose details are already known (an import from another system, or demo data)
	 * and matches it like any other.
	 */
	public Received receiveKnown(long accountId, StoredFile file, Driver driver, String source, Pods.Details details,
			double confidence, OffsetDateTime receivedAt) {
		long podId = pods.create(accountId, file.id(), driver == null ? null : driver.id(), source,
				details.lrNumber() == null ? null : "LR " + details.lrNumber(), receivedAt);
		pods.saveReading(podId, Pod.READ, null, details, BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP),
				PodReading.DocumentType.POD.name());
		Outcome outcome = match(accountId, pods.get(accountId, podId), driver);
		return new Received(pods.get(accountId, podId), outcome);
	}

	private void read(long accountId, long podId, StoredFile file, String caption) {
		String captionLr = lrFromCaption(caption);
		AiResult<PodReading> result = ai.read(new DocumentAi.Request<>(accountId, "pod", PodReading.INSTRUCTIONS,
				List.of(file), caption, PodReading.class));
		if (!result.ok()) {
			Pods.Details fromCaption = new Pods.Details(captionLr, null, null, null, null, null, null, null, null, null,
					null, null, null);
			pods.saveReading(podId, Pod.MANUAL, result.message(), fromCaption, null, null);
			return;
		}
		PodReading reading = result.value();
		String lrNumber = Lenient.text(reading.lrNumber());
		Pods.Details details = new Pods.Details(lrNumber != null ? lrNumber : captionLr, Lenient.date(reading.lrDate()),
				Lenient.date(reading.deliveryDate()), Lenient.text(reading.consignor()),
				Lenient.text(reading.consignee()), Lenient.text(reading.fromCity()), Lenient.text(reading.toCity()),
				Vehicle.normalize(Lenient.text(reading.vehicleNumber())), Lenient.text(reading.packages()),
				Lenient.number(reading.freightAmount()), reading.stampPresent(), reading.signaturePresent(),
				Lenient.text(reading.deliveryRemarks()));
		BigDecimal confidence = BigDecimal.valueOf(Math.max(0, Math.min(1, reading.confidence())))
			.setScale(2, RoundingMode.HALF_UP);
		pods.saveReading(podId, Pod.READ, null, details, confidence, reading.documentType().name());
	}

	private Outcome match(long accountId, Pod pod, Driver driver) {
		boolean readable = Pod.READ.equals(pod.readingStatus()) && pod.lrNumber() != null
				&& pod.confidenceValue() >= READABLE_CONFIDENCE;
		if (Pod.READ.equals(pod.readingStatus()) && !readable) {
			return Outcome.UNREADABLE;
		}
		List<Trip> candidates = trips.matchingLr(accountId, pod.lrNumber());
		if (candidates.size() > 1 && driver != null) {
			List<Trip> driversOwn = candidates.stream()
				.filter((trip) -> driver.id().equals(trip.driverId())
						|| (driver.vehicleId() != null && driver.vehicleId().equals(trip.vehicleId())))
				.toList();
			candidates = driversOwn.isEmpty() ? candidates : driversOwn;
		}
		if (candidates.size() != 1) {
			return pod.lrNumber() == null ? Outcome.NOT_READ : Outcome.NO_TRIP;
		}
		Trip trip = candidates.getFirst();
		boolean confident = readable && pod.acknowledged() && pod.confidenceValue() >= AUTO_CONFIRM_CONFIDENCE
				&& "POD".equals(pod.documentType());
		pods.link(pod.id(), trip.id(), confident ? Pod.CONFIRMED : Pod.NEEDS_REVIEW,
				confident ? OffsetDateTime.now(clock) : null);
		if (confident) {
			trips.markDelivered(accountId, trip.id(), deliveredOn(pod));
			return Outcome.CONFIRMED;
		}
		return Outcome.MATCHED;
	}

	/** The owner confirms a POD after checking it, attaching it to a trip or creating the trip. */
	@Transactional
	public long confirm(long accountId, long podId, PodReviewForm form) {
		Pod pod = pods.get(accountId, podId);
		Pods.Details details = form.details();
		pods.saveReading(podId, pod.readingStatus(), pod.readingNote(),
				new Pods.Details(details.lrNumber(), details.lrDate(), details.deliveryDate(), details.consignor(),
						details.consignee(), details.origin(), details.destination(),
						Vehicle.normalize(details.vehicleNumber()), details.packages(), details.freightAmount(),
						details.stampPresent(), details.signaturePresent(), details.remarks()),
				pod.confidence(), pod.documentType());
		long tripId;
		if (form.getTripId() != null) {
			tripId = trips.get(accountId, form.getTripId()).id();
		}
		else {
			tripId = createTrip(accountId, pod, form);
		}
		LocalDate delivered = details.deliveryDate() != null ? details.deliveryDate() : LocalDate.now(clock);
		trips.markDelivered(accountId, tripId, delivered);
		pods.link(podId, tripId, Pod.CONFIRMED, OffsetDateTime.now(clock));
		return tripId;
	}

	@Transactional
	public void reject(long accountId, long podId) {
		pods.get(accountId, podId);
		pods.link(podId, null, Pod.REJECTED, OffsetDateTime.now(clock));
	}

	private long createTrip(long accountId, Pod pod, PodReviewForm form) {
		TripForm trip = new TripForm();
		trip.setLrNumber(form.getLrNumber());
		trip.setLrDate(form.getLrDate());
		trip.setOrigin(form.getOrigin());
		trip.setDestination(form.getDestination());
		trip.setConsignee(form.getConsignee());
		trip.setFreightAmount(form.getFreightAmount());
		trip.setDeliveredOn(form.getDeliveryDate());
		if (form.getPartyId() != null) {
			trip.setPartyId(parties.get(accountId, form.getPartyId()).id());
		}
		if (form.getVehicleNumber() != null) {
			fleet.vehicleByRegistration(accountId, form.getVehicleNumber()).ifPresent((v) -> trip.setVehicleId(v.id()));
		}
		if (pod.driverId() != null) {
			Driver driver = fleet.driver(accountId, pod.driverId());
			trip.setDriverId(driver.id());
			if (trip.getVehicleId() == null) {
				trip.setVehicleId(driver.vehicleId());
			}
		}
		return trips.create(accountId, trip, Trip.DELIVERED);
	}

	private LocalDate deliveredOn(Pod pod) {
		return pod.deliveryDate() != null ? pod.deliveryDate() : pod.receivedAt().atZoneSameInstant(clock.getZone()).toLocalDate();
	}

	/** "LR 4521", "GR no. A/221", "bilty 9087" in a WhatsApp caption. */
	static String lrFromCaption(String caption) {
		if (caption == null) {
			return null;
		}
		Matcher matcher = LR_IN_CAPTION.matcher(caption);
		if (matcher.find()) {
			return matcher.group(1).toUpperCase(Locale.ROOT);
		}
		String trimmed = caption.trim();
		return trimmed.matches("\\d{3,12}") ? trimmed : null;
	}

	/** The WhatsApp reply to the driver for a received POD. */
	public static String replyFor(Received received, Driver driver, Trip trip) {
		Pod pod = received.pod();
		String name = driver == null ? "" : ", " + driver.name().split(" ")[0];
		String lrNumber = trip != null && trip.lrNumber() != null ? trip.lrNumber() : pod.lrNumber();
		String lr = lrNumber == null ? "" : " for LR " + lrNumber;
		String route = trip == null || trip.route().isEmpty() ? "" : " (" + trip.route() + ")";
		String reply = switch (received.outcome()) {
			case CONFIRMED -> "✅ POD received" + lr + route + ". Thank you" + name + "!";
			case MATCHED, NO_TRIP -> "✅ POD received" + lr + ". The office will check it. Thank you" + name + "!";
			case NOT_READ -> "✅ Photo received. The office will check it. Thank you" + name + "!";
			case UNREADABLE -> "⚠️ We could not read this photo clearly. Please send another photo of the full receipt "
					+ "in good light, with the LR number, stamp and signature visible.";
		};
		if (received.outcome() != Outcome.UNREADABLE && Pod.READ.equals(pod.readingStatus()) && !pod.acknowledged()) {
			reply += "\nNote: we cannot see the receiver's stamp or signature. Please get the copy stamped and signed, "
					+ "and send it again.";
		}
		return reply;
	}

	public enum Outcome {

		/** Matched to one trip and clear enough to need no check. */
		CONFIRMED,
		/** Matched to a trip, but the owner should look (no stamp, low confidence, not a POD). */
		MATCHED,
		/** Read, but no single trip has this LR number yet. */
		NO_TRIP,
		/** Not read automatically. */
		NOT_READ,
		/** Read, but too unclear to use. */
		UNREADABLE

	}

	public record Received(Pod pod, Outcome outcome) {
	}

}
