package com.liabletech.materials.catalog;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * A material the builder buys, in the unit it is compared in. {@code tolerancePct} allows for
 * normal variation (sand settles in a truck, steel is rolled within a weight tolerance) before a
 * gap counts as leakage.
 */
public record Material(Long id, Long accountId, String name, String category, String unit, String aliases,
		BigDecimal bagKg, Integer barDiameterMm, BigDecimal tolerancePct, boolean active) {

	public Unit baseUnit() {
		return Unit.valueOf(unit);
	}

	public List<String> aliasList() {
		if (aliases == null || aliases.isBlank()) {
			return List.of();
		}
		return Arrays.stream(aliases.split(",")).map(String::trim).filter((a) -> !a.isEmpty()).toList();
	}

}
