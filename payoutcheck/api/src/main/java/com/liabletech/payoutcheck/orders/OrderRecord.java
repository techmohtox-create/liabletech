package com.liabletech.payoutcheck.orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.liabletech.payoutcheck.common.Platform;
import com.liabletech.payoutcheck.ingest.OrderStatus;

/** An order as the restaurant recorded it. */
public record OrderRecord(long id, long outletId, Platform platform, String orderRef, LocalDateTime orderedAt,
		OrderStatus status, BigDecimal itemTotal, BigDecimal packaging, BigDecimal restaurantDiscount) {
}
