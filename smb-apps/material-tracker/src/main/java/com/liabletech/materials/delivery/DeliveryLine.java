package com.liabletech.materials.delivery;

import java.math.BigDecimal;

import com.liabletech.materials.catalog.Unit;

/** One material on a slip: the quantity the supplier wrote and the quantity counted at the gate. */
public record DeliveryLine(Long id, Long deliveryId, int lineNo, Long materialId, String description, BigDecimal slipQty,
		String unit, BigDecimal receivedQty) {

	public Unit parsedUnit() {
		return Unit.parse(unit);
	}

	/** What the slip claims minus what was counted; zero or less when nothing is short. */
	public BigDecimal shortBy() {
		if (slipQty == null || receivedQty == null) {
			return BigDecimal.ZERO;
		}
		return slipQty.subtract(receivedQty);
	}

}
