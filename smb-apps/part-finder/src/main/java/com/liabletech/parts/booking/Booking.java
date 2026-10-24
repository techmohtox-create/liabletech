package com.liabletech.parts.booking;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A part a mechanic booked from a quote, set aside on the shelf until it is handed over. */
public record Booking(Long id, Long accountId, Long enquiryId, Long mechanicId, Long partId, String description,
		int quantity, BigDecimal price, String status, String paymentMode, OffsetDateTime createdAt, OffsetDateTime doneAt) {

	public static final String BOOKED = "BOOKED";

	public static final String HANDED_OVER = "HANDED_OVER";

	public static final String CANCELLED = "CANCELLED";

	public BigDecimal total() {
		return price.multiply(BigDecimal.valueOf(quantity));
	}

}
