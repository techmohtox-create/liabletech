package com.liabletech.platform.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indian mobile numbers in one canonical form, {@code +91XXXXXXXXXX}. WhatsApp sends and expects
 * the same number without the plus ({@code 91XXXXXXXXXX}).
 */
public final class Phones {

	private static final Pattern MOBILE_IN_TEXT = Pattern
		.compile("(?<![\\d+])(?:\\+?91[\\s-]?)?0?[6-9]\\d{4}[\\s-]?\\d{5}(?!\\d)");

	private Phones() {
	}

	/**
	 * Normalises what people type ("98765 43210", "098765-43210", "+91 98765 43210") or what the
	 * WhatsApp API sends ("919876543210"). Numbers from other countries keep their own code. Returns
	 * null when the input does not look like a phone number.
	 */
	public static String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		String digits = raw.replaceAll("\\D", "");
		if (digits.startsWith("00")) {
			digits = digits.substring(2);
		}
		if (digits.length() == 11 && digits.startsWith("0")) {
			digits = digits.substring(1);
		}
		if (digits.length() == 10 && "6789".indexOf(digits.charAt(0)) >= 0) {
			digits = "91" + digits;
		}
		if (digits.length() < 10 || digits.length() > 15) {
			return null;
		}
		return "+" + digits;
	}

	/** The form the WhatsApp Cloud API uses in {@code to} and {@code wa_id}: digits only. */
	public static String waId(String normalized) {
		return normalized == null ? null : normalized.replace("+", "");
	}

	/** For display: "+91 98765 43210". */
	public static String pretty(String normalized) {
		if (normalized == null) {
			return "";
		}
		if (normalized.startsWith("+91") && normalized.length() == 13) {
			return "+91 " + normalized.substring(3, 8) + " " + normalized.substring(8);
		}
		return normalized;
	}

	/** Finds the first Indian mobile number in free text, e.g. a caption "cust 98765 43210". */
	public static String findIn(String text) {
		if (text == null) {
			return null;
		}
		Matcher matcher = MOBILE_IN_TEXT.matcher(text);
		return matcher.find() ? normalize(matcher.group()) : null;
	}

}
