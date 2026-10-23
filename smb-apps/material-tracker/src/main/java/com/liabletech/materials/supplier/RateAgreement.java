package com.liabletech.materials.supplier;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.liabletech.materials.catalog.Unit;

/** A rate agreed with a supplier for one material, before GST, valid between two dates. */
public record RateAgreement(Long id, Long accountId, Long supplierId, Long materialId, BigDecimal rate, String unit,
		LocalDate validFrom, LocalDate validTo) {

	public Unit rateUnit() {
		return Unit.valueOf(unit);
	}

	public boolean validOn(LocalDate date) {
		return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
	}

}
