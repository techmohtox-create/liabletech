package com.liabletech.exportdocs.check;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What field 45A says about the goods: the words the invoice must repeat, the quantity and unit
 * price with their tolerance, the trade term and the order it refers to.
 *
 * @param description the lines of 45A that describe the goods, without quantity, price and terms lines
 * @param keyWords the words of the goods description the invoice is expected to carry
 */
public record GoodsTerms(String description, List<String> keyWords, BigDecimal quantity, QtyUnit quantityUnit, boolean quantityAbout,
		BigDecimal unitPrice, String priceCurrency, QtyUnit priceUnit, boolean priceAbout, String incoterm,
		String incotermPlace, String incotermsVersion, String origin, String orderReference) {

	static final String CURRENCIES = "USD|EUR|GBP|AED|SAR|JPY|CHF|AUD|CAD|SGD|CNY|INR|US\\$";

	private static final Pattern QUANTITY = Pattern
		.compile("(?<![\\d.,])(\\d[\\d,]*(?:\\.\\d+)?)\\s*(" + QtyUnit.WRITTEN + ")(?![A-Z])");

	private static final Pattern PRICE = Pattern.compile("(" + CURRENCIES + ")\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(?:/|PER)\\s*("
			+ QtyUnit.WRITTEN + ")(?![A-Z])");

	private static final Pattern TERM = Pattern
		.compile("\\b(EX\\s?WORKS|EXW|FCA|FAS|FOB|CFR|C\\s?&\\s?F|CNF|C AND F|CIF|CPT|CIP|DAP|DPU|DAT|DDP)\\b[\\s,.:\\-]*([^\\n]*)");

	private static final Pattern VERSION = Pattern.compile("INCOTERMS?\\s*[-:]?\\s*(\\d{4})");

	private static final Pattern ORIGIN = Pattern.compile("(?:COUNTRY OF ORIGIN|ORIGIN)\\s*[:\\-]?\\s*([A-Z][A-Z ]{2,30})");

	private static final Pattern ORDER = Pattern.compile(
			"\\b(?:PROFORMA(?:\\s+INVOICE)?|PRO-FORMA(?:\\s+INVOICE)?|P\\.?\\s?O\\.?|PURCHASE\\s+ORDER|SALES\\s+CONTRACT|CONTRACT|INDENT|ORDER)"
					+ "\\s*(?:NO\\.?|NUMBER|#)?\\s*[:.]?\\s*([A-Z0-9][A-Z0-9/\\-.]{2,30})");

	private static final Pattern ABOUT = Pattern.compile("\\b(ABOUT|APPROX\\.?|APPROXIMATELY|CIRCA|CA\\.)\\b");

	private static final Pattern TERMS_LINE = Pattern.compile("^\\s*[+\\-]?\\s*(?:\\d+[.)]\\s*)?(QUANTITY|QTY|TOTAL|UNIT PRICE|PRICE|AMOUNT|VALUE"
			+ "|TERMS|TRADE TERMS|PRICE TERMS|DELIVERY TERMS|INCOTERMS?|SHIPMENT|AS PER|ORIGIN|COUNTRY OF ORIGIN|PACKING|PACKED"
			+ "|HS CODE|H\\.S\\.|HSN|ITC|EXW|EX WORKS|FCA|FAS|FOB|CFR|C&F|CNF|CIF|CPT|CIP|DAP|DPU|DAT|DDP)\\b.*");

	private static final Set<String> STOP = Set.of("AND", "THE", "FOR", "WITH", "FROM", "ANY", "ALL", "ARE", "NOT", "PER",
			"OTHER", "DETAILS", "GOODS", "DESCRIPTION", "ITEM", "ITEMS", "QUANTITY", "PRICE", "TOTAL", "AMOUNT", "ABOUT",
			"APPROX", "APPROXIMATELY", "EACH", "INCOTERMS", "INCOTERM", "TERMS", "OUR", "YOUR", "THIS", "THAT", "BEING",
			"USD", "EUR", "GBP", "AED", "SAR", "JPY", "CHF", "AUD", "CAD", "SGD", "CNY", "INR", "ONLY");

	public static GoodsTerms parse(String goods, String conditions) {
		String text = goods == null ? "" : goods.toUpperCase(Locale.ROOT);
		String other = conditions == null ? "" : conditions.toUpperCase(Locale.ROOT);

		BigDecimal quantity = null;
		QtyUnit quantityUnit = null;
		boolean quantityAbout = false;
		List<String> lines = text.lines().toList();
		List<String> quantityLines = lines.stream().filter((l) -> l.trim().matches("^[+\\-]?\\s*(?:\\d+[.)]\\s*)?(QUANTITY|QTY|TOTAL QUANTITY)\\b.*")).toList();
		for (String line : quantityLines.isEmpty() ? lines : quantityLines) {
			if (line.trim().matches("^[+\\-]?\\s*(PACKING|PACKED)\\b.*")) {
				continue;
			}
			Matcher matcher = QUANTITY.matcher(line);
			while (matcher.find()) {
				if (packingSpec(line, matcher.start(), matcher.end())) {
					continue;
				}
				QtyUnit unit = QtyUnit.parse(matcher.group(2));
				BigDecimal value = number(matcher.group(1));
				if (unit == null || value == null) {
					continue;
				}
				if (quantityUnit == null) {
					quantity = value;
					quantityUnit = unit;
				}
				else {
					BigDecimal converted = QtyUnit.convert(value, unit, quantityUnit);
					if (converted == null) {
						quantity = null;
						break;
					}
					quantity = quantity.add(converted);
				}
				quantityAbout |= ABOUT.matcher(line).find();
			}
			if (quantity == null && quantityUnit != null) {
				break;
			}
		}
		if (quantity == null) {
			quantityUnit = null;
			quantityAbout = false;
		}

		BigDecimal unitPrice = null;
		String priceCurrency = null;
		QtyUnit priceUnit = null;
		boolean priceAbout = false;
		Set<String> prices = new LinkedHashSet<>();
		Matcher price = PRICE.matcher(text);
		while (price.find()) {
			prices.add(price.group(1) + " " + number(price.group(2)).stripTrailingZeros().toPlainString() + " " + price.group(3));
			if (unitPrice == null) {
				priceCurrency = price.group(1).replace("US$", "USD");
				unitPrice = number(price.group(2));
				priceUnit = QtyUnit.parse(price.group(3));
				priceAbout = ABOUT.matcher(lineAround(text, price.start())).find();
			}
		}
		if (prices.size() > 1) {
			unitPrice = null;
			priceCurrency = null;
			priceUnit = null;
			priceAbout = false;
		}

		String incoterm = null;
		String incotermPlace = null;
		for (String source : List.of(text, other)) {
			Matcher term = TERM.matcher(source);
			if (term.find()) {
				incoterm = canonicalTerm(term.group(1));
				incotermPlace = place(term.group(2));
				break;
			}
		}
		Matcher version = VERSION.matcher(text + "\n" + other);
		String incotermsVersion = version.find() ? version.group(1) : null;

		String origin = null;
		if (text.contains("INDIAN ORIGIN") || text.contains("OF INDIA ORIGIN")) {
			origin = "INDIA";
		}
		else {
			Matcher matcher = ORIGIN.matcher(text);
			if (matcher.find()) {
				origin = matcher.group(1).trim().split("[,.]")[0].replaceAll("\\s+", " ").trim();
			}
		}

		String orderReference = null;
		Matcher order = ORDER.matcher(text);
		while (order.find()) {
			String candidate = order.group(1).replaceAll("[.\\-/]+$", "");
			if (candidate.matches(".*\\d.*")) {
				orderReference = candidate;
				break;
			}
		}

		return new GoodsTerms(description(lines), keyWords(lines), quantity, quantityUnit, quantityAbout, unitPrice, priceCurrency, priceUnit,
				priceAbout, incoterm, incotermPlace, incotermsVersion, origin, orderReference);
	}

	/** "IN 25 KG BAGS", "BAGS OF 50 KG", "25 KG EACH": a packing size, not the quantity ordered. */
	private static boolean packingSpec(String line, int start, int end) {
		String before = line.substring(Math.max(0, start - 8), start);
		String after = line.substring(end, Math.min(line.length(), end + 12));
		return before.matches(".*\\b(IN|OF|X|@|EACH|PER)\\s*$") || after.matches("^\\s*(EACH|NET EACH|PER|BAGS?|CARTONS?|DRUMS?|PP|HDPE|JUTE).*");
	}

	private static String description(List<String> lines) {
		return String.join("\n", lines.stream()
			.filter((line) -> !TERMS_LINE.matcher(line).matches() && !line.isBlank())
			.map((line) -> line.trim().replaceFirst("^[+\\-]\\s*", ""))
			.toList());
	}

	private static List<String> keyWords(List<String> lines) {
		Set<String> words = new LinkedHashSet<>();
		for (String line : lines) {
			if (TERMS_LINE.matcher(line).matches()) {
				continue;
			}
			String cleaned = PRICE.matcher(line).replaceAll(" ");
			cleaned = QUANTITY.matcher(cleaned).replaceAll(" ");
			cleaned = TERM.matcher(cleaned).replaceAll(" ");
			cleaned = ORDER.matcher(cleaned).replaceAll(" ");
			cleaned = cleaned.replaceAll("(" + CURRENCIES + ")\\s*[\\d,.]+", " ");
			for (String word : TradeText.words(cleaned)) {
				boolean letters = word.matches("[A-Z]{3,}");
				boolean grade = word.matches("\\d{2,4}[A-Z]{0,3}") && !word.matches("(19|20)\\d\\d");
				if ((letters && !STOP.contains(word)) || grade) {
					words.add(word);
				}
			}
		}
		return List.copyOf(words);
	}

	static String canonicalTerm(String written) {
		String term = written.replaceAll("\\s+", " ").trim();
		return switch (term) {
			case "C&F", "C & F", "CNF", "C AND F" -> "CFR";
			case "EX WORKS", "EXWORKS" -> "EXW";
			case "DAT" -> "DPU";
			default -> term;
		};
	}

	/** The named place after a trade term, up to "INCOTERMS", "AS PER", a bracket or a comma. */
	private static String place(String rest) {
		String place = rest.split("(?:INCOTERMS?|AS PER|\\(|;|,|\\.(?:\\s|$))")[0].replaceFirst("^(PORT OF|PORT)\\s+", "").trim();
		return place.isEmpty() ? null : place;
	}

	private static String lineAround(String text, int index) {
		int start = text.lastIndexOf('\n', index) + 1;
		int end = text.indexOf('\n', index);
		return text.substring(start, end < 0 ? text.length() : end);
	}

	private static BigDecimal number(String text) {
		try {
			return new BigDecimal(text.replace(",", ""));
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	/** Every quantity the credit gives, for the message when the units cannot be compared. */
	public String quantityText() {
		return quantity == null ? null : TradeText.qty(quantity) + " " + quantityUnit.label();
	}

	public List<String> missingFrom(String description) {
		Set<String> present = TradeText.words(description);
		List<String> missing = new ArrayList<>();
		for (String word : keyWords) {
			if (!present.contains(word)) {
				missing.add(word);
			}
		}
		return missing;
	}

}
