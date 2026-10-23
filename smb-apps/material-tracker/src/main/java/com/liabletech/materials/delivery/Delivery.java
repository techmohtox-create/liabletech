package com.liabletech.materials.delivery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** A delivery at a site gate: the slip as photographed and what the supervisor counted. */
public record Delivery(Long id, Long accountId, Long siteId, Long supplierId, Long memberId, Long fileId, String source,
		String caption, String readingStatus, String readingNote, String supplierNameRead, String challanNumber,
		LocalDate challanDate, String vehicleNumber, BigDecimal confidence, String status, OffsetDateTime receivedAt,
		OffsetDateTime confirmedAt, String notes) {

	/** The supervisor has been asked to confirm what arrived. */
	public static final String AWAITING_COUNT = "AWAITING_COUNT";

	/** Counted and confirmed: the quantities can be compared with bills. */
	public static final String CONFIRMED = "CONFIRMED";

	/** Not read automatically, or no lines: the office types it in. */
	public static final String NEEDS_REVIEW = "NEEDS_REVIEW";

	public static final String REJECTED = "REJECTED";

	public static final String READ = "READ";

	public static final String MANUAL = "MANUAL";

	public boolean confirmed() {
		return CONFIRMED.equals(status);
	}

}
