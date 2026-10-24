package com.liabletech.exportdocs.reading;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.liabletech.exportdocs.document.DocType;

/**
 * Tells which export document a file is from its text, WhatsApp caption or file name, without
 * calling the AI. Titles near the top of a document count most: a packing list mentions its invoice
 * number, but its title says PACKING LIST.
 */
public final class DocClassifier {

	private static final Map<DocType, List<String>> TITLES = new EnumMap<>(Map.of(
			DocType.LC, List.of("DOCUMENTARY CREDIT", "LETTER OF CREDIT", "MT700", "MT 700", "FIN 700"),
			DocType.INVOICE, List.of("COMMERCIAL INVOICE", "EXPORT INVOICE", "TAX INVOICE"),
			DocType.PACKING_LIST, List.of("PACKING LIST", "PACKING SLIP", "PACKING AND WEIGHT LIST", "WEIGHT LIST"),
			DocType.TRANSPORT, List.of("BILL OF LADING", "SEA WAYBILL", "AIR WAYBILL", "AIRWAY BILL", "MULTIMODAL TRANSPORT",
					"COMBINED TRANSPORT"),
			DocType.ORIGIN, List.of("CERTIFICATE OF ORIGIN"),
			DocType.INSURANCE, List.of("INSURANCE POLICY", "INSURANCE CERTIFICATE", "CERTIFICATE OF INSURANCE",
					"MARINE CARGO")));

	private static final Pattern MT700_TAG = Pattern.compile("(?m)^\\s*:?(20|31C|31D|32B|40A|40E|41[AD]|43P|43T|44[ABCEF]|45A|46A|47A|59|50):");

	private static final int TITLE_AREA = 700;

	private DocClassifier() {
	}

	/** The document's type from its text, or empty when the text does not make it clear. */
	public static Optional<DocType> byText(String text) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		String upper = text.toUpperCase(Locale.ROOT).replaceAll("[ \\t]+", " ");
		long tags = MT700_TAG.matcher(upper).results().count();
		if (tags >= 5) {
			return Optional.of(DocType.LC);
		}
		String top = upper.substring(0, Math.min(upper.length(), TITLE_AREA));
		DocType best = null;
		int bestScore = 0;
		for (Map.Entry<DocType, List<String>> entry : TITLES.entrySet()) {
			int score = 0;
			for (String title : entry.getValue()) {
				if (top.contains(title)) {
					score += 3;
				}
				else if (upper.contains(title)) {
					score += 1;
				}
			}
			if (entry.getKey() == DocType.INVOICE && top.contains("PROFORMA") && !top.contains("COMMERCIAL INVOICE")) {
				score = 0;
			}
			if (score > bestScore) {
				bestScore = score;
				best = entry.getKey();
			}
		}
		return bestScore >= 3 ? Optional.ofNullable(best) : Optional.empty();
	}

	/** The type a WhatsApp caption or file name names: "LC", "invoice", "PL", "BL draft", "COO". */
	public static Optional<DocType> byHint(String hint) {
		if (hint == null || hint.isBlank()) {
			return Optional.empty();
		}
		String text = " " + hint.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9/]+", " ") + " ";
		if (text.matches(".* (LC|L/C|DLC|CREDIT|MT700) .*")) {
			return Optional.of(DocType.LC);
		}
		if (text.matches(".* (PL|PACKING|PACKINGLIST) .*")) {
			return Optional.of(DocType.PACKING_LIST);
		}
		if (text.matches(".* (BL|B/L|BOL|LADING|AWB|WAYBILL|OBL|HBL|MBL) .*")) {
			return Optional.of(DocType.TRANSPORT);
		}
		if (text.matches(".* (COO|ORIGIN) .*")) {
			return Optional.of(DocType.ORIGIN);
		}
		if (text.matches(".* (INSURANCE|POLICY) .*")) {
			return Optional.of(DocType.INSURANCE);
		}
		if (text.matches(".* (INV|INVOICE|CI) .*")) {
			return Optional.of(DocType.INVOICE);
		}
		return Optional.empty();
	}

}
