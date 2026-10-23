package com.liabletech.freight.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

public record Invoice(Long id, Long accountId, String number, LocalDate invoiceDate, LocalDate dueDate, Long partyId,
		BigDecimal subtotal, String gstMode, BigDecimal gstRate, BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
		BigDecimal roundOff, BigDecimal advanceReceived, BigDecimal total, BigDecimal received, BigDecimal tds,
		String status, Long pdfFileId, OffsetDateTime sentAt, LocalDate paidOn, boolean chasePaused,
		LocalDate promisedOn, LocalDate nextFollowUpOn, int followUpCount, String notes, OffsetDateTime createdAt) {

	public static final String DRAFT = "DRAFT";

	public static final String SENT = "SENT";

	public static final String PART_PAID = "PART_PAID";

	public static final String PAID = "PAID";

	public static final String DISPUTED = "DISPUTED";

	public static final String WRITTEN_OFF = "WRITTEN_OFF";

	/** Statuses that still have money to collect. */
	public static final Set<String> OPEN = Set.of(SENT, PART_PAID, DISPUTED);

	public BigDecimal balance() {
		return total.subtract(received).subtract(tds);
	}

	public BigDecimal gstTotal() {
		return cgst.add(sgst).add(igst);
	}

	public boolean isOpen() {
		return OPEN.contains(status);
	}

	public boolean isDraft() {
		return DRAFT.equals(status);
	}

	/** Days past the due date (0 when not yet due). */
	public long daysOverdue(LocalDate today) {
		return Math.max(0, ChronoUnit.DAYS.between(dueDate, today));
	}

	/** Still being chased automatically. */
	public boolean chasing() {
		return (SENT.equals(status) || PART_PAID.equals(status)) && !chasePaused;
	}

}
