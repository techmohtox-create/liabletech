package com.liabletech.platform.text;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rupee amounts the way Indian invoices print them: lakh/crore digit grouping (1,23,45,678.00) and
 * amounts in words ("Rupees One Lakh Twenty Three Thousand Only").
 */
public final class IndianMoney {

	private static final String[] ONES = { "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
			"Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen",
			"Nineteen" };

	private static final String[] TENS = { "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty",
			"Ninety" };

	private IndianMoney() {
	}

	/** Indian digit grouping with the given number of decimals, e.g. {@code 1,23,456.50}. */
	public static String group(BigDecimal value, int decimals) {
		if (value == null) {
			return "";
		}
		BigDecimal scaled = value.setScale(decimals, RoundingMode.HALF_UP);
		boolean negative = scaled.signum() < 0;
		String plain = scaled.abs().toPlainString();
		String whole = plain;
		String fraction = "";
		int dot = plain.indexOf('.');
		if (dot >= 0) {
			whole = plain.substring(0, dot);
			fraction = plain.substring(dot);
		}
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
		return (negative ? "-" : "") + grouped + fraction;
	}

	/** "₹1,23,456.50", or "₹1,23,456" when there are no paise. */
	public static String rupees(BigDecimal value) {
		if (value == null) {
			return "";
		}
		boolean wholeRupees = value.setScale(2, RoundingMode.HALF_UP).remainder(BigDecimal.ONE).signum() == 0;
		return "₹" + group(value, wholeRupees ? 0 : 2);
	}

	/** "Rupees Twelve Thousand Five Hundred and Fifty Paise Only". */
	public static String inWords(BigDecimal value) {
		if (value == null) {
			return "";
		}
		BigDecimal scaled = value.abs().setScale(2, RoundingMode.HALF_UP);
		long rupees = scaled.longValue();
		int paise = scaled.remainder(BigDecimal.ONE).movePointRight(2).intValue();
		StringBuilder words = new StringBuilder("Rupees ");
		words.append(rupees == 0 ? "Zero" : wholeNumber(rupees));
		if (paise > 0) {
			words.append(" and ").append(belowHundred(paise)).append(" Paise");
		}
		return words.append(" Only").toString();
	}

	private static String wholeNumber(long n) {
		StringBuilder out = new StringBuilder();
		long crore = n / 1_00_00_000;
		n %= 1_00_00_000;
		long lakh = n / 1_00_000;
		n %= 1_00_000;
		long thousand = n / 1_000;
		n %= 1_000;
		long hundred = n / 100;
		long rest = n % 100;
		if (crore > 0) {
			append(out, wholeNumber(crore) + " Crore");
		}
		if (lakh > 0) {
			append(out, belowHundred((int) lakh) + " Lakh");
		}
		if (thousand > 0) {
			append(out, belowHundred((int) thousand) + " Thousand");
		}
		if (hundred > 0) {
			append(out, ONES[(int) hundred] + " Hundred");
		}
		if (rest > 0) {
			append(out, belowHundred((int) rest));
		}
		return out.toString();
	}

	private static String belowHundred(int n) {
		if (n < 20) {
			return ONES[n];
		}
		return TENS[n / 10] + (n % 10 == 0 ? "" : " " + ONES[n % 10]);
	}

	private static void append(StringBuilder out, String part) {
		if (!out.isEmpty()) {
			out.append(' ');
		}
		out.append(part);
	}

}
