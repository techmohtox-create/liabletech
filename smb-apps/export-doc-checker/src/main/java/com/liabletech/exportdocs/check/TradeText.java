package com.liabletech.exportdocs.check;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.liabletech.platform.text.Fuzzy;
import com.liabletech.platform.text.Lenient;

/** Reading values off trade documents and writing them back in findings. */
public final class TradeText {

	private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);

	private static final Pattern YYMMDD = Pattern.compile("^(\\d{2})(\\d{2})(\\d{2})$");

	private static final Pattern YMD = Pattern.compile("^(\\d{4})[/.](\\d{1,2})[/.](\\d{1,2})$");

	private static final Pattern MDY = Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$");

	private TradeText() {
	}

	public static boolean blank(String value) {
		return Lenient.text(value) == null;
	}

	public static String text(String value) {
		return Lenient.text(value);
	}

	public static BigDecimal number(String value) {
		return Lenient.number(value);
	}

	/** A date as written on an export document: ISO, Indian day-first, SWIFT YYMMDD or US month-first. */
	public static LocalDate date(String value) {
		String text = Lenient.text(value);
		if (text == null) {
			return null;
		}
		LocalDate parsed = Lenient.date(text);
		if (parsed != null) {
			return parsed;
		}
		try {
			Matcher swift = YYMMDD.matcher(text);
			if (swift.matches()) {
				return LocalDate.of(2000 + Integer.parseInt(swift.group(1)), Integer.parseInt(swift.group(2)),
						Integer.parseInt(swift.group(3)));
			}
			Matcher ymd = YMD.matcher(text);
			if (ymd.matches()) {
				return LocalDate.of(Integer.parseInt(ymd.group(1)), Integer.parseInt(ymd.group(2)),
						Integer.parseInt(ymd.group(3)));
			}
			Matcher mdy = MDY.matcher(text);
			if (mdy.matches()) {
				return LocalDate.of(Integer.parseInt(mdy.group(3)), Integer.parseInt(mdy.group(1)),
						Integer.parseInt(mdy.group(2)));
			}
		}
		catch (DateTimeException ex) {
			return null;
		}
		return null;
	}

	public static String show(LocalDate date) {
		return date == null ? "–" : SHOWN.format(date);
	}

	/** An amount in a foreign currency, grouped in thousands: "USD 85,000.00". */
	public static String money(String currency, BigDecimal amount) {
		if (amount == null) {
			return "–";
		}
		DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		return (currency == null || currency.isBlank() ? "" : currency + " ") + format.format(amount);
	}

	/** A quantity without needless decimals: "20,000", "20.5". */
	public static String qty(BigDecimal value) {
		if (value == null) {
			return "–";
		}
		BigDecimal rounded = value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
		DecimalFormat format = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		return format.format(rounded);
	}

	/** The first line of a party (name above the address). */
	public static String firstLine(String party) {
		String text = Lenient.text(party);
		if (text == null) {
			return null;
		}
		return Arrays.stream(text.split("\\R")).map(String::trim).filter((l) -> !l.isEmpty()).findFirst().orElse(null);
	}

	/** Upper-case letters and digits separated by single spaces. */
	public static String normalize(String text) {
		return Fuzzy.normalize(text);
	}

	/** The distinct words of a text, normalised. */
	public static Set<String> words(String text) {
		String normalized = Fuzzy.normalize(text);
		if (normalized.isEmpty()) {
			return Set.of();
		}
		return Arrays.stream(normalized.split(" ")).collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/** Same reference number as written by different people: "INV/2026/118" and "INV-2026-118". */
	public static boolean sameReference(String a, String b) {
		return Fuzzy.sameCode(a, b);
	}

	/**
	 * Whether a party on a document is the party the credit names. The credit's name must appear in
	 * full; a near miss is a spelling difference worth fixing.
	 */
	public static NameMatch name(String expectedParty, String actualParty) {
		String expected = firstLine(expectedParty);
		String actual = Lenient.text(actualParty);
		if (expected == null || actual == null) {
			return NameMatch.UNKNOWN;
		}
		if (Fuzzy.coverage(expected, actual) >= 0.999) {
			return NameMatch.SAME;
		}
		double similarity = Fuzzy.similarity(expected, firstLine(actual));
		if (similarity >= 0.85 || Fuzzy.coverage(expected, actual) >= 0.75) {
			return NameMatch.SIMILAR;
		}
		return NameMatch.DIFFERENT;
	}

	public enum NameMatch {

		SAME, SIMILAR, DIFFERENT, UNKNOWN

	}

}
