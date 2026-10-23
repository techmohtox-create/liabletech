package com.liabletech.freight.pod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** A proof-of-delivery photo or scan and what was read from it. */
public record Pod(Long id, Long accountId, Long tripId, Long fileId, Long driverId, String source, String caption,
		String readingStatus, String readingNote, String documentType, String lrNumber, LocalDate lrDate,
		LocalDate deliveryDate, String consignor, String consignee, String origin, String destination,
		String vehicleNumber, String packages, BigDecimal freightAmount, Boolean stampPresent, Boolean signaturePresent,
		String remarks, BigDecimal confidence, String reviewStatus, OffsetDateTime reviewedAt,
		OffsetDateTime receivedAt) {

	public static final String NEEDS_REVIEW = "NEEDS_REVIEW";

	public static final String CONFIRMED = "CONFIRMED";

	public static final String REJECTED = "REJECTED";

	/** Read by the AI. */
	public static final String READ = "READ";

	/** Not read automatically (AI off or it failed): the details are typed in at review. */
	public static final String MANUAL = "MANUAL";

	public boolean acknowledged() {
		return Boolean.TRUE.equals(stampPresent) || Boolean.TRUE.equals(signaturePresent);
	}

	public boolean hasShortageRemark() {
		if (remarks == null) {
			return false;
		}
		String lower = remarks.toLowerCase(java.util.Locale.ROOT);
		return lower.contains("short") || lower.contains("damage") || lower.contains("broken") || lower.contains("less");
	}

	public double confidenceValue() {
		return confidence == null ? 0 : confidence.doubleValue();
	}

}
