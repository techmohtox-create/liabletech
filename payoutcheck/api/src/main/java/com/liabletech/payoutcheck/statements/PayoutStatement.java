package com.liabletech.payoutcheck.statements;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.liabletech.payoutcheck.common.Platform;

public record PayoutStatement(long id, long orgId, long outletId, Platform platform, LocalDate periodStart, LocalDate periodEnd,
		LocalDate payoutDate, String utr, BigDecimal creditedAmount, String fileName, int lineCount, BigDecimal grossSales,
		BigDecimal totalDeductions, BigDecimal netPayout, OffsetDateTime checkedAt, OffsetDateTime createdAt) {
}
