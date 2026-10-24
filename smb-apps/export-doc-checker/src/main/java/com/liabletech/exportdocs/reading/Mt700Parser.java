package com.liabletech.exportdocs.reading;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the text of a SWIFT MT700 documentary credit: the raw FIN message (":20:LC1234") or an
 * advising bank's printout of it (":20: Documentary Credit Number" with the value on the next line).
 * No AI is needed for a credit that comes as text, and none of its terms can be misread.
 */
public final class Mt700Parser {

	private static final Pattern TAG_LINE = Pattern.compile("^\\s*:?(\\d{2}[A-Z]?)\\s*:\\s?(.*)$");

	private static final Pattern TRAILER = Pattern.compile("(?i)^\\s*(-\\}|\\{5:|end of message|message trailer|\\*+\\s*end).*");

	private static final Pattern PAGE = Pattern.compile("(?i)^\\s*page\\s+\\d+(\\s+of\\s+\\d+)?\\s*$");

	private static final Pattern SENDER = Pattern.compile("(?im)^\\s*(?:sender|issuing bank|from)\\s*[:\\-]\\s*(.+)$");

	/** Labels advising banks print after the tag, as letters-only words. */
	private static final Map<String, String> LABELS = Map.ofEntries(Map.entry("20", "DOCUMENTARY CREDIT NUMBER"),
			Map.entry("23", "REFERENCE TO PRE ADVICE"), Map.entry("27", "SEQUENCE OF TOTAL"),
			Map.entry("31C", "DATE OF ISSUE"), Map.entry("31D", "DATE AND PLACE OF EXPIRY"),
			Map.entry("32B", "CURRENCY CODE AMOUNT"), Map.entry("39A", "PERCENTAGE CREDIT AMOUNT TOLERANCE"),
			Map.entry("39C", "ADDITIONAL AMOUNTS COVERED"), Map.entry("40A", "FORM OF DOCUMENTARY CREDIT"),
			Map.entry("40E", "APPLICABLE RULES"), Map.entry("41", "AVAILABLE WITH BY"), Map.entry("42C", "DRAFTS AT"),
			Map.entry("42", "DRAWEE"), Map.entry("43P", "PARTIAL SHIPMENTS"), Map.entry("43T", "TRANSHIPMENT"),
			Map.entry("44A", "PLACE OF TAKING IN CHARGE DISPATCH FROM PLACE OF RECEIPT"),
			Map.entry("44E", "PORT OF LOADING AIRPORT OF DEPARTURE"),
			Map.entry("44F", "PORT OF DISCHARGE AIRPORT OF DESTINATION"),
			Map.entry("44B", "PLACE OF FINAL DESTINATION FOR TRANSPORTATION TO PLACE OF DELIVERY"),
			Map.entry("44C", "LATEST DATE OF SHIPMENT"), Map.entry("44D", "SHIPMENT PERIOD"),
			Map.entry("45A", "DESCRIPTION OF GOODS AND OR SERVICES"), Map.entry("46A", "DOCUMENTS REQUIRED"),
			Map.entry("47A", "ADDITIONAL CONDITIONS"), Map.entry("48", "PERIOD FOR PRESENTATION IN DAYS"),
			Map.entry("49", "CONFIRMATION INSTRUCTIONS"), Map.entry("50", "APPLICANT"), Map.entry("59", "BENEFICIARY"),
			Map.entry("51", "APPLICANT BANK"), Map.entry("52", "ISSUING BANK"), Map.entry("53", "REIMBURSING BANK"),
			Map.entry("57", "ADVISE THROUGH BANK"), Map.entry("58", "REQUESTED CONFIRMATION PARTY"),
			Map.entry("71D", "CHARGES"), Map.entry("78", "INSTRUCTIONS TO THE PAYING ACCEPTING NEGOTIATING BANK"),
			Map.entry("72Z", "SENDER TO RECEIVER INFORMATION"), Map.entry("49G", "SPECIAL PAYMENT CONDITIONS FOR BENEFICIARY"),
			Map.entry("49H", "SPECIAL PAYMENT CONDITIONS FOR BANK ONLY"));

	private static final Set<String> TAGS = Set.of("20", "23", "27", "31C", "31D", "32B", "39A", "39C", "40A", "40E",
			"41A", "41D", "42A", "42C", "42D", "42M", "42P", "43P", "43T", "44A", "44B", "44C", "44D", "44E", "44F", "45A",
			"46A", "47A", "48", "49", "49G", "49H", "50", "51A", "51D", "52A", "52D", "53A", "53D", "57A", "57B", "57D",
			"58A", "58D", "59", "71D", "72Z", "78");

	/** Fields whose line breaks matter: parties and the long free-text fields. */
	private static final Set<String> MULTILINE = Set.of("45A", "46A", "47A", "50", "59", "52", "51", "78", "72Z", "49G",
			"49H");

	private Mt700Parser() {
	}

	/** The credit's terms, or empty when the text is not an MT700. */
	public static Optional<LcReading> parse(String text) {
		if (text == null) {
			return Optional.empty();
		}
		Map<String, String> f = fields(text);
		if (f.size() < 5 || !f.containsKey("20") || !f.containsKey("32B")) {
			return Optional.empty();
		}
		String expiryDate = "";
		String expiryPlace = "";
		Matcher expiry = Pattern.compile("(?s)^(\\d{6})\\s*(.*)$").matcher(f.getOrDefault("31D", ""));
		if (expiry.matches()) {
			expiryDate = date(expiry.group(1));
			expiryPlace = expiry.group(2).replaceAll("\\s+", " ").trim();
		}
		String currency = "";
		String amount = "";
		Matcher money = Pattern.compile("^([A-Z]{3})\\s*([\\d.,]+)").matcher(f.getOrDefault("32B", ""));
		if (money.find()) {
			currency = money.group(1);
			BigDecimal value = swiftAmount(money.group(2));
			amount = value == null ? money.group(2) : value.toPlainString();
		}
		String issuingBank = f.getOrDefault("52", f.getOrDefault("51", ""));
		if (issuingBank.isEmpty()) {
			int firstTag = firstTagIndex(text);
			Matcher sender = SENDER.matcher(firstTag > 0 ? text.substring(0, firstTag) : "");
			if (sender.find()) {
				issuingBank = withoutBic(sender.group(1).trim());
			}
		}
		boolean complete = f.containsKey("31D") && (f.containsKey("44C") || f.containsKey("44D")) && f.containsKey("45A")
				&& f.containsKey("46A");
		return Optional.of(new LcReading(f.getOrDefault("20", ""), date(f.getOrDefault("31C", "")), expiryDate,
				expiryPlace, issuingBank, party(f.get("50")), party(f.get("59")), currency, amount,
				f.getOrDefault("39A", ""), f.getOrDefault("41", ""), f.getOrDefault("42C", ""), f.getOrDefault("43P", ""),
				f.getOrDefault("43T", ""), f.getOrDefault("44A", ""), f.getOrDefault("44E", ""), f.getOrDefault("44F", ""),
				f.getOrDefault("44B", ""), date(f.getOrDefault("44C", "")), f.getOrDefault("44D", ""),
				f.getOrDefault("45A", ""), f.getOrDefault("46A", ""), f.getOrDefault("47A", ""), f.getOrDefault("71D", ""),
				f.getOrDefault("48", ""), f.getOrDefault("49", ""), f.getOrDefault("40E", ""), f.getOrDefault("40A", ""),
				complete ? 0.98 : 0.8));
	}

	/** Field values by tag; variants such as 41A and 41D share the key "41". */
	static Map<String, String> fields(String text) {
		Map<String, String> fields = new LinkedHashMap<>();
		String tag = null;
		List<String> lines = new ArrayList<>();
		for (String raw : text.split("\\R")) {
			Matcher matcher = TAG_LINE.matcher(raw);
			if (matcher.matches() && TAGS.contains(matcher.group(1))) {
				put(fields, tag, lines);
				tag = key(matcher.group(1));
				lines = new ArrayList<>();
				String rest = stripLabel(tag, matcher.group(2).trim());
				if (!rest.isEmpty()) {
					lines.add(rest);
				}
				continue;
			}
			if (tag == null) {
				continue;
			}
			if (TRAILER.matcher(raw).matches()) {
				put(fields, tag, lines);
				tag = null;
				lines = new ArrayList<>();
				continue;
			}
			String line = raw.trim();
			if (!line.isEmpty() && !PAGE.matcher(line).matches()) {
				lines.add(line);
			}
		}
		put(fields, tag, lines);
		return fields;
	}

	private static void put(Map<String, String> fields, String tag, List<String> lines) {
		if (tag == null || lines.isEmpty() || fields.containsKey(tag)) {
			return;
		}
		String value = MULTILINE.contains(tag) ? String.join("\n", lines) : String.join(" ", lines).replaceAll("\\s+", " ");
		fields.put(tag, value.trim());
	}

	private static String key(String tag) {
		return switch (tag) {
			case "41A", "41D" -> "41";
			case "42A", "42D" -> "42";
			case "51A", "51D" -> "51";
			case "52A", "52D" -> "52";
			case "53A", "53D" -> "53";
			case "57A", "57B", "57D" -> "57";
			case "58A", "58D" -> "58";
			default -> tag;
		};
	}

	/**
	 * Removes the label an advising bank prints after the tag ("Date of Issue", "Currency Code,
	 * Amount"), keeping a value that follows it on the same line. SWIFT values are in capitals; a
	 * label is not.
	 */
	static String stripLabel(String tag, String rest) {
		if (rest.isEmpty()) {
			return rest;
		}
		boolean capitals = rest.equals(rest.toUpperCase(Locale.ROOT));
		String label = LABELS.get(tag);
		if (label != null) {
			List<String> words = Arrays.asList(label.split(" "));
			String[] tokens = rest.split("\\s+");
			int w = 0;
			int t = 0;
			while (t < tokens.length && w < words.size()) {
				String letters = tokens[t].replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
				if (letters.isEmpty()) {
					t++;
				}
				else if (letters.equals(words.get(w))) {
					t++;
					w++;
				}
				else {
					break;
				}
			}
			// A value may itself start with a label word ("CHARGES OUTSIDE INDIA ..."): in capitals,
			// only a two-word match, or a one-word label standing alone, is taken as the label.
			boolean isLabel = capitals ? (w >= 2 || (w == 1 && words.size() == 1 && t == tokens.length)) : w > 0;
			if (isLabel) {
				String remainder = String.join(" ", Arrays.copyOfRange(tokens, t, tokens.length))
					.replaceFirst("^[:\\-\\s]+", "")
					.trim();
				if (!remainder.matches(".*[A-Za-z0-9].*")) {
					return "";
				}
				return remainder.equals(remainder.toUpperCase(Locale.ROOT)) ? remainder : "";
			}
		}
		if (capitals) {
			return rest;
		}
		// An unknown wording of the label: drop it unless it carries a value.
		return rest.matches(".*\\d.*") ? rest : "";
	}

	private static int firstTagIndex(String text) {
		Matcher matcher = Pattern.compile("(?m)^\\s*:?(20|27|40A|31C):").matcher(text);
		return matcher.find() ? matcher.start() : -1;
	}

	/** "HAFSDEHHXXX HAFENSTADT BANK AG" without its SWIFT code, when a name follows it. */
	static String withoutBic(String sender) {
		String name = sender.replaceFirst("^[A-Z]{6}[A-Z0-9]{2}(?:[A-Z0-9]{3})?\\s+", "");
		return name.matches(".*[A-Za-z]{3,}.*\\s.*") ? name : sender;
	}

	/** A party field without the account-number line SWIFT puts first ("/123456789"). */
	private static String party(String value) {
		if (value == null) {
			return "";
		}
		return Arrays.stream(value.split("\n")).filter((line) -> !line.startsWith("/")).reduce((a, b) -> a + "\n" + b)
			.orElse("")
			.trim();
	}

	/** YYMMDD as an ISO date; anything else is returned unchanged. */
	static String date(String value) {
		Matcher matcher = Pattern.compile("^(\\d{2})(\\d{2})(\\d{2})\\b").matcher(value.trim());
		if (!matcher.find()) {
			return value.trim();
		}
		try {
			return LocalDate.of(2000 + Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
					Integer.parseInt(matcher.group(3)))
				.toString();
		}
		catch (DateTimeException ex) {
			return value.trim();
		}
	}

	/**
	 * A SWIFT amount: "48000,00" uses a comma for decimals and no grouping. Bank printouts sometimes
	 * format it as "48,000.00"; the last separator is the decimal one when both appear.
	 */
	static BigDecimal swiftAmount(String value) {
		String digits = value.trim();
		int comma = digits.lastIndexOf(',');
		int dot = digits.lastIndexOf('.');
		String normalized;
		if (comma >= 0 && dot >= 0) {
			normalized = comma > dot ? digits.replace(".", "").replace(',', '.') : digits.replace(",", "");
		}
		else if (comma >= 0) {
			String after = digits.substring(comma + 1);
			boolean decimal = after.length() <= 2 && digits.indexOf(',') == comma;
			normalized = decimal ? digits.replace(',', '.') : digits.replace(",", "");
		}
		else {
			normalized = digits;
		}
		if (normalized.endsWith(".")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		try {
			return new BigDecimal(normalized);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
