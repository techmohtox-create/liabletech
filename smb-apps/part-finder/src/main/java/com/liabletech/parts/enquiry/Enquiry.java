package com.liabletech.parts.enquiry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import com.liabletech.parts.catalog.PartType;

/** A mechanic asking for a part: what they sent, what it was understood to be, and how it was answered. */
public record Enquiry(Long id, Long accountId, Long mechanicId, Long inboundId, String kind, Long fileId, String text,
		String transcript, String readingStatus, String readingNote, String partType, String partName,
		String partNumbers, String vehicleMake, String vehicleModel, Integer vehicleYear, String position, int quantity,
		BigDecimal confidence, String question, String status, OffsetDateTime receivedAt, OffsetDateTime firstReplyAt,
		String repliedBy, OffsetDateTime nudgedAt, OffsetDateTime closedAt) {

	public static final String PHOTO = "PHOTO";

	public static final String VOICE = "VOICE";

	public static final String TEXT = "TEXT";

	/** Waiting for the counter. */
	public static final String NEW = "NEW";

	/** Options sent; waiting for the mechanic to book one. */
	public static final String QUOTED = "QUOTED";

	public static final String BOOKED = "BOOKED";

	/** Told it is not in stock: demand the shop missed. */
	public static final String NOT_AVAILABLE = "NOT_AVAILABLE";

	public static final String CLOSED = "CLOSED";

	public static final String AUTO = "AUTO";

	public static final String STAFF = "STAFF";

	public Identification identification() {
		List<String> numbers = partNumbers == null || partNumbers.isBlank() ? List.of()
				: Arrays.stream(partNumbers.split(",")).map(String::trim).filter((s) -> !s.isEmpty()).toList();
		return new Identification(PartType.parse(partType), partName, numbers, vehicleMake, vehicleModel, vehicleYear,
				position, quantity, confidence == null ? 0 : confidence.doubleValue(), question, readingStatus, readingNote);
	}

	public boolean open() {
		return NEW.equals(status) || QUOTED.equals(status);
	}

	/** Seconds from the message to the first real answer, or null while unanswered. */
	public Long responseSeconds() {
		return firstReplyAt == null ? null : Math.max(0, Duration.between(receivedAt, firstReplyAt).getSeconds());
	}

	/** When it arrived, as the page's running timers read it: "2026-10-24T10:15:00+05:30". */
	public String receivedIso() {
		return receivedAt.withNano(0).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	/** What the mechanic sent, in words: the transcript, the message or the caption. */
	public String words() {
		if (transcript != null && !transcript.isBlank()) {
			return transcript;
		}
		return text;
	}

}
