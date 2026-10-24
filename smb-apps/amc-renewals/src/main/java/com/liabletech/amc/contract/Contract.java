package com.liabletech.amc.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/** An annual maintenance contract for one appliance. */
public record Contract(Long id, Long accountId, Long customerId, Long applianceId, Long planId, String planName,
		BigDecimal price, LocalDate startsOn, LocalDate endsOn, int visitsIncluded, String status, Long renewedById,
		String renewalToken, BigDecimal paidAmount, LocalDate paidOn, String paymentMode, String source,
		OffsetDateTime createdAt) {

	/** In force, or expired and not yet renewed: still worth chasing. */
	public static final String ACTIVE = "ACTIVE";

	public static final String RENEWED = "RENEWED";

	public static final String CANCELLED = "CANCELLED";

	public boolean open() {
		return ACTIVE.equals(status);
	}

	public boolean expired(LocalDate today) {
		return endsOn.isBefore(today);
	}

	public long daysLeft(LocalDate today) {
		return ChronoUnit.DAYS.between(today, endsOn);
	}

	public boolean paid() {
		return paidAmount != null && paidAmount.signum() > 0;
	}

	/** "UPI", "cash", "card", "bank transfer". */
	public String paymentModeLabel() {
		if (paymentMode == null) {
			return null;
		}
		return switch (paymentMode) {
			case "UPI" -> "UPI";
			case "BANK" -> "bank transfer";
			default -> paymentMode.toLowerCase(java.util.Locale.ROOT);
		};
	}

	/** "due in 9 days", "expires today", "expired 12 days ago". */
	public String dueText(LocalDate today) {
		long days = daysLeft(today);
		if (days == 0) {
			return "expires today";
		}
		if (days > 0) {
			return "due in " + days + (days == 1 ? " day" : " days");
		}
		return "expired " + -days + (days == -1 ? " day" : " days") + " ago";
	}

}
