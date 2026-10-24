package com.liabletech.exportdocs.check;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.liabletech.exportdocs.document.DocType;

/**
 * What fields 46A (documents required) and 47A (additional conditions) ask for, read with patterns
 * rather than AI so the same credit always gives the same checklist.
 */
public final class LcRequirements {

	private static final Pattern ITEM_START = Pattern.compile("(?m)^\\s*(?:\\+|\\d{1,2}[).]|\\(\\d{1,2}\\)|-\\s)\\s*");

	private static final Map<String, Integer> NUMBER_WORDS = Map.of("ONE", 1, "TWO", 2, "THREE", 3, "FOUR", 4, "FIVE", 5,
			"SIX", 6, "DUPLICATE", 2, "TRIPLICATE", 3, "QUADRUPLICATE", 4);

	private static final String COUNT = "(\\d{1,2}|ONE|TWO|THREE|FOUR|FIVE|SIX)";

	private static final Pattern IN_FOLD = Pattern.compile("\\bIN\\s+(\\d{1,2}|ONE|TWO|THREE|FOUR|FIVE|SIX|DUPLICATE|TRIPLICATE|QUADRUPLICATE)\\b\\s*(ORIGINALS?|FOLDS?|COPIES|COPY|SETS?)?");

	private static final Pattern ORIGINALS = Pattern.compile("\\b" + COUNT + "\\s+(?:SIGNED\\s+)?ORIGINALS?\\b");

	private static final Pattern COPIES = Pattern.compile("\\b" + COUNT + "\\s+(?:NON[- ]NEGOTIABLE\\s+)?(?:PHOTO)?COP(?:Y|IES)\\b");

	private static final Pattern SET_OF = Pattern.compile("\\b(\\d)\\s*/\\s*(\\d)\\b");

	private static final Pattern ORDER = Pattern.compile("TO\\s+(?:THE\\s+)?ORDER(?:\\s+OF\\s+([^,;\\n]+?))?(?=\\s*(?:,|;|\\.(?:\\s|$)|\\bMARKED\\b|\\bNOTIFY|\\bSHOWING|\\bBLANK|\\bAND\\b|\\bINDICATING|\\bENDORSED|$))");

	private static final Pattern CONSIGNED = Pattern.compile("CONSIGNED\\s+TO\\s+([^,;\\n]+?)(?=\\s*(?:,|;|\\.(?:\\s|$)|\\bMARKED\\b|\\bNOTIFY|\\bSHOWING|$))");

	private static final Pattern NOTIFY = Pattern.compile("NOTIFY(?:ING)?(?:\\s+PARTY)?\\s*[:\\-]?\\s*([^,;\\n]+?)(?=\\s*(?:,|;|\\.(?:\\s|$)|\\bMARKED\\b|\\bSHOWING|\\bINDICATING|\\bWITH\\b|$))");

	private static final Pattern FREIGHT = Pattern.compile("FREIGHT\\s+(PREPAID|COLLECT|PAYABLE AT DESTINATION|TO COLLECT|TO BE COLLECTED|TO PAY)");

	private static final Pattern PERCENT = Pattern.compile("\\b(\\d{2,3})\\s*(?:PCT|PERCENT|PER CENT|%)");

	private static final Pattern ISSUED_BY = Pattern.compile("(?:ISSUED|CERTIFIED|LEGALI[SZ]ED|ATTESTED)\\s+BY\\s+(?:THE\\s+|A\\s+|ANY\\s+)?([^,;\\n]+?)(?=\\s*(?:,|;|\\.(?:\\s|$)|\\bSHOWING|\\bCERTIFYING|\\bINDICATING|\\bSTATING|\\bIN\\s+(?:\\d|ONE|TWO|THREE|DUPLICATE|TRIPLICATE)|$))");

	private static final Pattern SHOW_LC = Pattern.compile("(?:SHOWING|INDICATING|BEARING|QUOTING|MENTIONING|STATING|MUST SHOW)\\s+(?:THIS\\s+|THE\\s+|OUR\\s+)?(?:L\\s?/\\s?C|LC|CREDIT|DOCUMENTARY CREDIT|D\\s?/\\s?C|DC)\\s*(?:NO|NUMBER|REF)");

	private static final Pattern ALL_SHOW_LC = Pattern.compile("ALL\\s+(?:THE\\s+)?DOCUMENTS?\\s+(?:MUST|SHOULD|TO|SHALL)\\s+(?:SHOW|BEAR|INDICATE|MENTION|QUOTE)\\s+(?:THIS\\s+|THE\\s+|OUR\\s+)?(?:L\\s?/\\s?C|LC|CREDIT|DOCUMENTARY CREDIT|D\\s?/\\s?C|DC)\\s*(?:NO|NUMBER)");

	private static final Pattern FEE = Pattern.compile("DISCREPANC(?:Y|IES)\\s+(?:HANDLING\\s+)?FEES?\\s+(?:OF\\s+)?(?:FOR\\s+)?([A-Z]{3})\\s*(\\d[\\d.,]*)");

	private static final Pattern SOFT = Pattern.compile("(?:SIGNED|ISSUED|COUNTER[- ]?SIGNED|APPROVED|AUTHENTICATED|ENDORSED|CERTIFIED)\\s+BY\\s+(?:THE\\s+)?(?:APPLICANT|BUYER|OPENER|APPLICANT'?S\\s+\\w+|BUYER'?S\\s+\\w+)"
			+ "|SUBJECT\\s+TO\\s+(?:THE\\s+)?(?:APPLICANT|BUYER)'?S?\\s+(?:APPROVAL|CONFIRMATION|ACCEPTANCE|CONSENT)"
			+ "|UPON\\s+(?:RECEIPT\\s+OF\\s+)?(?:THE\\s+)?(?:APPLICANT|BUYER)'?S?\\s+(?:APPROVAL|CONFIRMATION|ACCEPTANCE|CONSENT|NOTIFICATION)");

	private LcRequirements() {
	}

	/** The documents field 46A lists, in order. */
	public static List<RequiredDoc> documents(String field46a) {
		List<RequiredDoc> docs = new ArrayList<>();
		int number = 1;
		for (String item : items(field46a)) {
			String text = item.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
			if (text.length() < 6) {
				continue;
			}
			docs.add(document(number++, text));
		}
		return docs;
	}

	/** What field 47A adds: LC number on every document, no documents dated before the credit, the fee. */
	public static LcConditions conditions(String field47a, String field46a) {
		String text = field47a == null ? "" : field47a.toUpperCase(Locale.ROOT).replaceAll("[ \\t]+", " ");
		String joined = text.replaceAll("\\s*\\n\\s*", " ");
		boolean lcNumberOnAll = ALL_SHOW_LC.matcher(joined).find();
		boolean noEarlierDocs = false;
		boolean charterPartyRefused = false;
		boolean thirdPartyRefused = false;
		for (String condition : items(text)) {
			String c = condition.replaceAll("\\s+", " ");
			boolean refused = c.contains("NOT ACCEPTABLE") || c.contains("NOT ACCEPTED") || c.contains("NOT ALLOWED");
			if (refused && c.matches(".*\\b(PRIOR TO|EARLIER THAN|BEFORE)\\b.*") && c.matches(".*\\b(ISSU\\w*|L\\s?/\\s?C DATE|CREDIT DATE|LC DATE)\\b.*")) {
				noEarlierDocs = true;
			}
			if (refused && c.contains("CHARTER PARTY")) {
				charterPartyRefused = true;
			}
			if (refused && c.contains("THIRD PARTY DOCUMENT")) {
				thirdPartyRefused = true;
			}
		}
		String fee = null;
		Matcher feeMatcher = FEE.matcher(joined);
		if (feeMatcher.find()) {
			BigDecimal amount = TradeText.number(feeMatcher.group(2));
			fee = feeMatcher.group(1) + " " + (amount == null ? feeMatcher.group(2) : TradeText.qty(amount));
		}
		Set<String> soft = new LinkedHashSet<>();
		for (String source : List.of(text, field46a == null ? "" : field46a.toUpperCase(Locale.ROOT))) {
			for (String item : items(source)) {
				String c = item.replaceAll("\\s+", " ").trim();
				if (SOFT.matcher(c).find()) {
					soft.add(c.length() > 220 ? c.substring(0, 217) + "..." : c);
				}
			}
		}
		return new LcConditions(lcNumberOnAll, noEarlierDocs, fee, List.copyOf(soft), charterPartyRefused,
				thirdPartyRefused);
	}

	static List<String> items(String field) {
		if (field == null || field.isBlank()) {
			return List.of();
		}
		List<String> items = new ArrayList<>();
		Matcher matcher = ITEM_START.matcher(field);
		List<Integer> starts = new ArrayList<>();
		while (matcher.find()) {
			starts.add(matcher.start());
		}
		if (starts.size() >= 2 || (starts.size() == 1 && starts.getFirst() == 0)) {
			if (starts.getFirst() > 0 && !field.substring(0, starts.getFirst()).isBlank()) {
				items.add(field.substring(0, starts.getFirst()));
			}
			for (int i = 0; i < starts.size(); i++) {
				int end = i + 1 < starts.size() ? starts.get(i + 1) : field.length();
				items.add(ITEM_START.matcher(field.substring(starts.get(i), end)).replaceFirst(""));
			}
		}
		else {
			// No markers: a new item starts on a line that names a document; other lines continue the item.
			StringBuilder current = new StringBuilder();
			for (String line : field.split("\\R")) {
				if (!current.isEmpty() && kind(line.toUpperCase(Locale.ROOT)) != RequiredDoc.Kind.OTHER) {
					items.add(current.toString());
					current = new StringBuilder();
				}
				current.append(current.isEmpty() ? "" : " ").append(line.trim());
			}
			if (!current.isEmpty()) {
				items.add(current.toString());
			}
		}
		return items.stream().map(String::trim).filter((i) -> !i.isEmpty()).toList();
	}

	private static RequiredDoc document(int number, String text) {
		RequiredDoc.Kind kind = kind(text);
		Integer originals = null;
		Integer copies = null;
		Matcher fold = IN_FOLD.matcher(text);
		if (fold.find()) {
			int count = count(fold.group(1));
			String what = fold.group(2) == null ? "" : fold.group(2);
			if (what.startsWith("COP")) {
				copies = count;
			}
			else {
				originals = count;
			}
		}
		Matcher original = ORIGINALS.matcher(text);
		if (original.find()) {
			originals = count(original.group(1));
		}
		Matcher copy = COPIES.matcher(text);
		if (copy.find()) {
			copies = count(copy.group(1));
		}
		boolean fullSet = text.contains("FULL SET");
		Matcher set = SET_OF.matcher(text);
		if (set.find() && set.group(1).equals(set.group(2))) {
			fullSet = true;
			originals = Integer.valueOf(set.group(1));
		}
		boolean signed = text.matches(".*\\bSIGNED\\b.*") && !text.matches(".*\\bUNSIGNED\\b.*");

		String consignee = null;
		Matcher order = ORDER.matcher(text);
		if (order.find()) {
			consignee = order.group(1) == null ? "ORDER" : "ORDER OF " + order.group(1).trim();
		}
		else {
			Matcher consigned = CONSIGNED.matcher(text);
			if (consigned.find()) {
				consignee = consigned.group(1).trim();
			}
		}
		String notify = null;
		Matcher notifyMatcher = NOTIFY.matcher(text);
		if (notifyMatcher.find()) {
			notify = notifyMatcher.group(1).contains("APPLICANT") ? "APPLICANT" : notifyMatcher.group(1).trim();
		}
		String freight = null;
		Matcher freightMatcher = FREIGHT.matcher(text);
		if (freightMatcher.find()) {
			freight = freightMatcher.group(1).equals("PREPAID") ? "PREPAID" : "COLLECT";
		}
		BigDecimal percent = null;
		List<String> risks = new ArrayList<>();
		if (kind == RequiredDoc.Kind.INSURANCE) {
			Matcher pct = PERCENT.matcher(text);
			if (pct.find()) {
				percent = new BigDecimal(pct.group(1));
			}
			Matcher icc = Pattern.compile("(?:INSTITUTE\\s+)?CARGO\\s+CLAUSES?\\s*\\(?\\s*([ABC])\\s*\\)?|\\bICC\\s*\\(?\\s*([ABC])\\s*\\)?").matcher(text);
			if (icc.find()) {
				risks.add("ICC (" + (icc.group(1) != null ? icc.group(1) : icc.group(2)) + ")");
			}
			else if (text.contains("ALL RISKS")) {
				risks.add("ALL RISKS");
			}
			if (text.matches(".*\\bWAR\\b.*")) {
				risks.add("WAR");
			}
			if (text.matches(".*\\b(STRIKES?|SRCC|RIOTS)\\b.*")) {
				risks.add("STRIKES");
			}
			if (text.matches(".*\\b(TPND|THEFT|PILFERAGE)\\b.*")) {
				risks.add("THEFT");
			}
		}
		String issuedBy = null;
		Matcher issued = ISSUED_BY.matcher(text);
		if (issued.find()) {
			issuedBy = issued.group(1).trim();
		}
		return new RequiredDoc(number, kind, text, originals, copies, fullSet, signed, consignee, notify, freight,
				text.contains("ON BOARD") || text.contains("SHIPPED"), text.matches(".*\\bCLEAN\\b.*"),
				SHOW_LC.matcher(text).find(), percent, List.copyOf(risks), issuedBy, text.contains("BLANK ENDORSED"));
	}

	static RequiredDoc.Kind kind(String text) {
		if (text.matches(".*\\bBENEFICIARY'?S?\\s+(CERTIFICATE|STATEMENT|DECLARATION|LETTER)\\b.*")
				|| text.contains("CERTIFICATE FROM BENEFICIARY") || text.contains("CERTIFICATE FROM THE BENEFICIARY")) {
			return RequiredDoc.Kind.BENEFICIARY_CERTIFICATE;
		}
		if (text.contains("CERTIFICATE OF ORIGIN") || text.contains("ORIGIN CERTIFICATE") || text.matches(".*\\bGSP\\b.*")) {
			return RequiredDoc.Kind.ORIGIN;
		}
		if (text.contains("INSURANCE")) {
			return RequiredDoc.Kind.INSURANCE;
		}
		if (text.contains("AIR WAYBILL") || text.contains("AIRWAY BILL") || text.contains("AIR TRANSPORT DOCUMENT")
				|| text.matches(".*\\bAWB\\b.*")) {
			return RequiredDoc.Kind.AIR_TRANSPORT;
		}
		if (text.contains("BILL OF LADING") || text.contains("BILLS OF LADING") || text.matches(".*\\bB\\s?/\\s?L\\b.*")
				|| text.contains("SEA WAYBILL") || text.contains("MULTIMODAL") || text.contains("COMBINED TRANSPORT")) {
			return RequiredDoc.Kind.TRANSPORT;
		}
		if (text.contains("PACKING")) {
			return RequiredDoc.Kind.PACKING_LIST;
		}
		if (text.contains("INSPECTION")) {
			return RequiredDoc.Kind.INSPECTION;
		}
		if (text.contains("PHYTOSANITARY")) {
			return RequiredDoc.Kind.PHYTOSANITARY;
		}
		if (text.contains("FUMIGATION")) {
			return RequiredDoc.Kind.FUMIGATION;
		}
		if (text.contains("ANALYSIS") || text.contains("QUALITY") || text.contains("HEALTH CERTIFICATE")) {
			return RequiredDoc.Kind.QUALITY;
		}
		if (text.contains("WEIGHT")) {
			return RequiredDoc.Kind.WEIGHT;
		}
		if (text.contains("INVOICE")) {
			return RequiredDoc.Kind.INVOICE;
		}
		if (text.contains("DRAFT") || text.contains("BILL OF EXCHANGE")) {
			return RequiredDoc.Kind.DRAFT;
		}
		return RequiredDoc.Kind.OTHER;
	}

	private static int count(String written) {
		Integer word = NUMBER_WORDS.get(written);
		return word != null ? word : Integer.parseInt(written);
	}

	/** What 47A asks beyond the documents. */
	public record LcConditions(boolean lcNumberOnAllDocuments, boolean noDocumentsBeforeIssue, String discrepancyFee,
			List<String> softClauses, boolean charterPartyRefused, boolean thirdPartyDocumentsRefused) {

		public static LcConditions none() {
			return new LcConditions(false, false, null, List.of(), false, false);
		}

	}

	/** Which document type of the app a requirement is checked against, if any. */
	public static DocType readAs(RequiredDoc.Kind kind) {
		return switch (kind) {
			case INVOICE -> DocType.INVOICE;
			case PACKING_LIST -> DocType.PACKING_LIST;
			case TRANSPORT, AIR_TRANSPORT -> DocType.TRANSPORT;
			case ORIGIN -> DocType.ORIGIN;
			case INSURANCE -> DocType.INSURANCE;
			default -> null;
		};
	}

}
