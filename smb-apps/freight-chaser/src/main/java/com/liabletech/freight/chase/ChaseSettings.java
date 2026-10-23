package com.liabletech.freight.chase;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import com.liabletech.freight.invoice.GstMode;

/**
 * How a fleet chases payment. {@code reminderDays} are days relative to the due date: -2 is two
 * days before, 0 is the due date, 15 is fifteen days late. After the last one, a reminder goes
 * every {@code repeatEveryDays} until the invoice is paid, paused or disputed.
 */
public record ChaseSettings(Long accountId, boolean enabled, String reminderDays, int repeatEveryDays,
		boolean sendWhatsapp, boolean sendEmail, String defaultGstMode, BigDecimal defaultGstRate, String invoicePrefix,
		String invoiceTerms, boolean ownerDigest) {

	public static final String DEFAULT_DAYS = "-2,0,7,15,30,45,60,90";

	public static ChaseSettings defaults(long accountId) {
		return new ChaseSettings(accountId, true, DEFAULT_DAYS, 15, true, true, GstMode.NONE.name(), BigDecimal.valueOf(5),
				"FC", "Please pay within the credit period by NEFT or UPI and quote the invoice number.",
				true);
	}

	/** The reminder offsets, sorted, ignoring anything that is not a whole number. */
	public List<Integer> offsets() {
		return Arrays.stream(reminderDays.split(","))
			.map(String::trim)
			.filter((value) -> value.matches("-?\\d{1,3}"))
			.map(Integer::valueOf)
			.distinct()
			.sorted()
			.toList();
	}

	public GstMode gstMode() {
		return GstMode.valueOf(defaultGstMode);
	}

}
