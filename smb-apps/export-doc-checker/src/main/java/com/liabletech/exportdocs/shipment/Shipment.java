package com.liabletech.exportdocs.shipment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

/** One export shipment presented under one letter of credit. */
public record Shipment(Long id, Long accountId, String reference, String title, String buyerName, String buyerEmail,
		String lcNumber, String issuingBank, String currency, BigDecimal lcAmount, LocalDate lcExpiry,
		LocalDate latestShipment, LocalDate shippedOn, LocalDate presentationDeadline, LocalDate plannedPresentation,
		String forwarderName, String forwarderPhone, String forwarderEmail, String status, int criticalCount,
		int warningCount, OffsetDateTime firstCheckedAt, OffsetDateTime checkedAt, LocalDate submittedOn, String notes,
		OffsetDateTime createdAt, OffsetDateTime updatedAt) {

	/** Documents are still coming in, or everything so far is fine but more are needed. */
	public static final String COLLECTING = "COLLECTING";

	/** Checked, with discrepancies or points to fix. */
	public static final String CHECKED = "CHECKED";

	/** Every document is in and nothing needs fixing. */
	public static final String READY = "READY";

	public static final String SUBMITTED = "SUBMITTED";

	public static final String PAID = "PAID";

	public static final String CANCELLED = "CANCELLED";

	public static final Set<String> ACTIVE = Set.of(COLLECTING, CHECKED, READY);

	public boolean active() {
		return ACTIVE.contains(status);
	}

	public boolean checked() {
		return checkedAt != null;
	}

	/** The next date that matters: ship by, present by, or the expiry. */
	public Deadline nextDeadline(LocalDate today) {
		if (shippedOn == null && latestShipment != null && !latestShipment.isBefore(today.minusDays(30))) {
			return new Deadline("Ship by", latestShipment);
		}
		if (presentationDeadline != null) {
			return new Deadline("Present by", presentationDeadline);
		}
		if (lcExpiry != null) {
			return new Deadline("LC expires", lcExpiry);
		}
		return null;
	}

	public String displayName() {
		return title != null && !title.isBlank() ? title : reference;
	}

	public record Deadline(String label, LocalDate date) {
	}

}
