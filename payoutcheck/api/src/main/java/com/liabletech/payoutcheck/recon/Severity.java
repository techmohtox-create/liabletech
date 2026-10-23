package com.liabletech.payoutcheck.recon;

import java.math.BigDecimal;

public enum Severity {

	HIGH, MEDIUM, LOW;

	private static final BigDecimal HIGH_FROM = new BigDecimal("100");

	private static final BigDecimal MEDIUM_FROM = new BigDecimal("20");

	/** For checks whose importance is simply how much money is involved. */
	public static Severity forAmount(BigDecimal amount) {
		if (amount.compareTo(HIGH_FROM) >= 0) {
			return HIGH;
		}
		return amount.compareTo(MEDIUM_FROM) >= 0 ? MEDIUM : LOW;
	}

	public Severity atLeast(Severity floor) {
		return compareTo(floor) > 0 ? floor : this;
	}

	public Severity atMost(Severity ceiling) {
		return compareTo(ceiling) < 0 ? ceiling : this;
	}

}
