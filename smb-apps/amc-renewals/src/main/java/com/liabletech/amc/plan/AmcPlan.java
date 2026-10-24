package com.liabletech.amc.plan;

import java.math.BigDecimal;

/** An annual maintenance plan the business sells: what it costs, how long it runs, how many visits it includes. */
public record AmcPlan(Long id, Long accountId, String name, String family, BigDecimal price, int months, int visits,
		String covers, boolean active) {

	public boolean appliesTo(String applianceFamily) {
		return "ANY".equals(family) || family.equals(applianceFamily);
	}

}
