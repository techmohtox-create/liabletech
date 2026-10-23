package com.liabletech.platform.text;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns text read off a document into numbers and dates. Indian paperwork writes "1,23,456.50/-",
 * "Rs. 2500", "12/10/26" and "12-Oct-2026" for the same kinds of values; all of them parse here.
 */
public final class Lenient {

	private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

	private static final Pattern CURRENCY = Pattern.compile("(?i)\\brs\\.?|\\binr\\b|₹");

	private static final List<DateTimeFormatter> DATE_FORMATS = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
			pattern("d/M/uuuu"), pattern("d-M-uuuu"), pattern("d.M.uuuu"), twoDigitYear("d/M/"), twoDigitYear("d-M-"),
			twoDigitYear("d.M."), pattern("d MMM uuuu"), pattern("d-MMM-uuuu"), pattern("d MMMM uuuu"),
			pattern("MMM d, uuuu"), pattern("MMMM d, uuuu"), pattern("d MMM, uuuu"), twoDigitYear("d-MMM-"),
			twoDigitYear("d MMM "));

	private Lenient() {
	}

	/**
	 * The first number in the text, ignoring currency marks and Indian digit grouping. Amounts and
	 * quantities on these documents are never negative, so a dash is never read as a minus sign.
	 */
	public static BigDecimal number(String text) {
		if (text == null) {
			return null;
		}
		Matcher matcher = NUMBER.matcher(CURRENCY.matcher(text).replaceAll(" "));
		if (!matcher.find()) {
			return null;
		}
		try {
			return new BigDecimal(matcher.group().replace(",", ""));
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	/** A date in any of the formats Indian documents use, or null. Two-digit years are 20xx. */
	public static LocalDate date(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String cleaned = text.trim().replaceAll("\\s+", " ").replaceAll("(?i)(\\d)(st|nd|rd|th)\\b", "$1");
		for (DateTimeFormatter format : DATE_FORMATS) {
			try {
				return LocalDate.parse(cleaned, format);
			}
			catch (DateTimeParseException ex) {
				// try the next format
			}
		}
		return null;
	}

	/** Trimmed text, or null when blank or a placeholder such as "-" or "N/A". */
	public static String text(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty() || trimmed.equals("-") || trimmed.equalsIgnoreCase("n/a")
				|| trimmed.equalsIgnoreCase("null") || trimmed.equalsIgnoreCase("unknown")) {
			return null;
		}
		return trimmed;
	}

	private static DateTimeFormatter pattern(String pattern) {
		return new DateTimeFormatterBuilder().parseCaseInsensitive()
			.appendPattern(pattern)
			.toFormatter(Locale.ENGLISH);
	}

	private static DateTimeFormatter twoDigitYear(String prefix) {
		return new DateTimeFormatterBuilder().parseCaseInsensitive()
			.appendPattern(prefix)
			.appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
			.toFormatter(Locale.ENGLISH);
	}

}
