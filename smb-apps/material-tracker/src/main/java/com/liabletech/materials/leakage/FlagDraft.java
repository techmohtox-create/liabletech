package com.liabletech.materials.leakage;

import java.math.BigDecimal;

/** A finding before it is stored. */
public record FlagDraft(String kind, Long materialId, Long deliveryId, String message, BigDecimal expectedQty,
		BigDecimal actualQty, String unit, BigDecimal amount) {
}
