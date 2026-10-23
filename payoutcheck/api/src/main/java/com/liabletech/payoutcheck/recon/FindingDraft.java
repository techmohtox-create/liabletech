package com.liabletech.payoutcheck.recon;

import java.math.BigDecimal;

/** A finding before it is stored. {@code fingerprint} identifies it across re-runs of the same statement. */
public record FindingDraft(Long lineId, String orderRef, FindingType type, Severity severity, BigDecimal expectedAmount,
		BigDecimal actualAmount, BigDecimal amountAtRisk, String explanation, String fingerprint) {
}
