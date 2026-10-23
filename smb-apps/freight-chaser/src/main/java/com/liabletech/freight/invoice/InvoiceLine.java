package com.liabletech.freight.invoice;

import java.math.BigDecimal;

public record InvoiceLine(Long id, Long invoiceId, Long tripId, String description, BigDecimal amount, int position) {
}
