package com.liabletech.freight.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Money received against an invoice. TDS the payer deducted counts towards settling it. */
public record Payment(Long id, Long accountId, Long invoiceId, LocalDate receivedOn, BigDecimal amount, BigDecimal tds,
		String mode, String reference, OffsetDateTime createdAt) {
}
