package com.liabletech.payoutcheck.recon;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.liabletech.payoutcheck.common.Platform;

public record Finding(long id, long outletId, long statementId, Long lineId, Platform platform, String orderRef,
		FindingType type, Severity severity, BigDecimal expectedAmount, BigDecimal actualAmount, BigDecimal amountAtRisk,
		String explanation, FindingStatus status, String ticketRef, BigDecimal recoveredAmount, String notes,
		String fingerprint, OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime resolvedAt) {
}
