package com.liabletech.freight.trip;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One load carried: booked on a lorry receipt (LR), delivered, invoiced and finally paid. */
public record Trip(Long id, Long accountId, String lrNumber, LocalDate lrDate, Long partyId, Long vehicleId,
		Long driverId, String origin, String destination, String consignee, String goods, BigDecimal freightAmount,
		BigDecimal advanceAmount, BigDecimal detentionAmount, BigDecimal otherCharges, LocalDate deliveredOn,
		String status, Long invoiceId, String notes, OffsetDateTime createdAt) {

	public static final String IN_TRANSIT = "IN_TRANSIT";

	/** Delivered, with a proof of delivery on file: ready to bill. */
	public static final String DELIVERED = "DELIVERED";

	public static final String INVOICED = "INVOICED";

	public static final String PAID = "PAID";

	public static final String CANCELLED = "CANCELLED";

	public String route() {
		if (origin == null && destination == null) {
			return "";
		}
		return (origin == null ? "?" : origin) + " → " + (destination == null ? "?" : destination);
	}

	/** Freight plus detention and other charges, before advances. */
	public BigDecimal billable() {
		return nz(freightAmount).add(nz(detentionAmount)).add(nz(otherCharges));
	}

	public boolean billableNow() {
		return DELIVERED.equals(status) && invoiceId == null;
	}

	private static BigDecimal nz(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

}
