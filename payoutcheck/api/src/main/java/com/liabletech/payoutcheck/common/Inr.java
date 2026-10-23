package com.liabletech.payoutcheck.common;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formats rupees the Indian way: ₹1,23,456.50. */
public final class Inr {

	private Inr() {
	}

	public static String format(BigDecimal amount) {
		BigDecimal value = Money.of(amount);
		String sign = value.signum() < 0 ? "-" : "";
		String plain = value.abs().toPlainString();
		int dot = plain.indexOf('.');
		String whole = plain.substring(0, dot);
		String paise = plain.substring(dot);
		StringBuilder grouped = new StringBuilder();
		int length = whole.length();
		if (length <= 3) {
			grouped.append(whole);
		}
		else {
			String head = whole.substring(0, length - 3);
			String tail = whole.substring(length - 3);
			for (int i = 0; i < head.length(); i++) {
				if (i > 0 && (head.length() - i) % 2 == 0) {
					grouped.append(',');
				}
				grouped.append(head.charAt(i));
			}
			grouped.append(',').append(tail);
		}
		return sign + "₹" + grouped + paise;
	}

	public static String percent(BigDecimal pct) {
		DecimalFormat format = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		return format.format(pct) + "%";
	}

}
