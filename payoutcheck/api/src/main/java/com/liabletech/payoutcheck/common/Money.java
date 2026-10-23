package com.liabletech.payoutcheck.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rupee arithmetic: every amount is kept to paise and rounded half-up, as platform statements are. */
public final class Money {

	public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private Money() {
	}

	public static BigDecimal of(BigDecimal value) {
		return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
	}

	public static BigDecimal of(String value) {
		return of(new BigDecimal(value));
	}

	/** {@code base * pct / 100}, rounded to paise. */
	public static BigDecimal percent(BigDecimal base, BigDecimal pct) {
		return base.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
	}

	public static BigDecimal sum(BigDecimal... values) {
		BigDecimal total = ZERO;
		for (BigDecimal value : values) {
			total = total.add(of(value));
		}
		return total;
	}

	public static BigDecimal positive(BigDecimal value) {
		return value.signum() > 0 ? value : ZERO;
	}

	public static boolean exceeds(BigDecimal actual, BigDecimal expected, BigDecimal tolerance) {
		return actual.subtract(expected).compareTo(tolerance) > 0;
	}

}
