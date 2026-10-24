package com.liabletech.amc.label;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A label photo from a technician, and what was read from it and from its caption. */
public record LabelScan(Long id, Long accountId, Long technicianId, Long fileId, String caption, String readingStatus,
		String readingNote, String kind, String brand, String model, String serialNumber, String capacity,
		String refrigerant, String manufactured, String customerPhone, String customerName, boolean amcSold,
		BigDecimal amount, BigDecimal confidence, String status, Long applianceId, OffsetDateTime createdAt) {

	public static final String NEEDS_REVIEW = "NEEDS_REVIEW";

	public static final String LINKED = "LINKED";

	public static final String REJECTED = "REJECTED";

	public static final String READ = "READ";

	public static final String MANUAL = "MANUAL";

}
