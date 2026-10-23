package com.liabletech.materials.bill;

import java.math.BigDecimal;

import com.liabletech.materials.catalog.Unit;

public record BillLine(Long id, Long billId, int lineNo, Long materialId, String description, BigDecimal qty, String unit,
		BigDecimal rate, BigDecimal amount, String challanRef) {

	public Unit parsedUnit() {
		return Unit.parse(unit);
	}

}
