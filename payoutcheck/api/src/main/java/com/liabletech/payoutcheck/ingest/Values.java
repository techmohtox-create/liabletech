package com.liabletech.payoutcheck.ingest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import com.liabletech.payoutcheck.common.Money;

/** Lenient parsing of the numbers and dates found in Indian platform exports. */
public final class Values {

	private static final List<DateTimeFormatter> DATE_TIMES = List.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME,
			pattern("yyyy-MM-dd HH:mm[:ss]"), pattern("dd-MM-yyyy HH:mm[:ss]"), pattern("dd/MM/yyyy HH:mm[:ss]"),
			pattern("dd-MMM-yyyy HH:mm[:ss]"), pattern("dd MMM yyyy HH:mm[:ss]"), pattern("dd/MM/yyyy hh:mm[:ss] a"),
			pattern("dd-MM-yyyy hh:mm[:ss] a"), pattern("MMM dd, yyyy hh:mm a"));

	private static final List<DateTimeFormatter> DATES = List.of(DateTimeFormatter.ISO_LOCAL_DATE, pattern("dd-MM-yyyy"),
			pattern("dd/MM/yyyy"), pattern("dd.MM.yyyy"), pattern("dd-MMM-yyyy"), pattern("dd MMM yyyy"), pattern("d MMM yyyy"),
			pattern("dd-MMM-yy"), pattern("MMM dd, yyyy"), pattern("d/M/yyyy"));

	private Values() {
	}

	private static DateTimeFormatter pattern(String pattern) {
		return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH);
	}

	/**
	 * Parses "₹1,234.50", "1234.5", "(120.00)" and "-120" alike. Blank, "-" and "NA" are zero.
	 * @throws IllegalArgumentException when the text is not a number
	 */
	public static BigDecimal money(String raw) {
		if (raw == null) {
			return Money.ZERO;
		}
		String text = raw.trim();
		if (text.isEmpty() || text.equals("-") || text.equalsIgnoreCase("na") || text.equalsIgnoreCase("n/a")
				|| text.equalsIgnoreCase("nil")) {
			return Money.ZERO;
		}
		boolean negative = false;
		if (text.startsWith("(") && text.endsWith(")")) {
			negative = true;
			text = text.substring(1, text.length() - 1);
		}
		text = text.replace("₹", "").replace("Rs.", "").replace("Rs", "").replace("INR", "").replace(",", "").replace(" ", "");
		if (text.endsWith("-")) {
			negative = true;
			text = text.substring(0, text.length() - 1);
		}
		try {
			BigDecimal value = Money.of(new BigDecimal(text));
			return negative ? value.negate() : value;
		}
		catch (NumberFormatException ex) {
			throw new IllegalArgumentException("'" + raw + "' is not an amount");
		}
	}

	/** Deductions are reported as positive or negative depending on the platform; we store them positive. */
	public static BigDecimal deduction(String raw) {
		return money(raw).abs();
	}

	public static LocalDateTime dateTime(String raw) {
		String text = raw == null ? "" : raw.trim();
		if (text.isEmpty()) {
			throw new IllegalArgumentException("date is missing");
		}
		LocalDateTime parsed = tryDateTime(text);
		return parsed != null ? parsed : LocalDateTime.of(date(text), LocalTime.NOON);
	}

	private static LocalDateTime tryDateTime(String text) {
		for (DateTimeFormatter format : DATE_TIMES) {
			try {
				return LocalDateTime.parse(text, format);
			}
			catch (DateTimeParseException ignored) {
				// try the next format
			}
		}
		return null;
	}

	public static LocalDate date(String raw) {
		String text = raw == null ? "" : raw.trim();
		if (text.isEmpty()) {
			throw new IllegalArgumentException("date is missing");
		}
		LocalDateTime withTime = tryDateTime(text);
		if (withTime != null) {
			return withTime.toLocalDate();
		}
		for (DateTimeFormatter format : DATES) {
			try {
				return LocalDate.parse(text, format);
			}
			catch (DateTimeParseException ignored) {
				// try the next format
			}
		}
		throw new IllegalArgumentException("'" + raw + "' is not a date");
	}

	/** Normalises a header for matching: lower case, letters and digits only. */
	public static String key(String header) {
		return header == null ? "" : header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

}
