package com.liabletech.exportdocs.check;

import java.math.BigDecimal;
import java.util.List;

import com.liabletech.exportdocs.document.DocType;

/**
 * One document field 46A asks for, with the details a check can hold the uploaded document to.
 *
 * @param consignee "ORDER", "ORDER OF <bank>" or the named consignee a transport document must show
 * @param notifyParty "APPLICANT" or the party to notify
 * @param freight "PREPAID" or "COLLECT" when the credit says how freight is to be marked
 */
public record RequiredDoc(int number, Kind kind, String text, Integer originals, Integer copies, boolean fullSet,
		boolean signed, String consignee, String notifyParty, String freight, boolean onBoard, boolean clean,
		boolean showLcNumber, BigDecimal insurancePercent, List<String> risks, String issuedBy, boolean blankEndorsed) {

	public enum Kind {

		INVOICE("Commercial invoice"), PACKING_LIST("Packing list"), TRANSPORT("Bill of lading"),
		AIR_TRANSPORT("Air waybill"), ORIGIN("Certificate of origin"), INSURANCE("Insurance document"),
		BENEFICIARY_CERTIFICATE("Beneficiary's certificate"), INSPECTION("Inspection certificate"),
		PHYTOSANITARY("Phytosanitary certificate"), FUMIGATION("Fumigation certificate"),
		QUALITY("Quality / analysis certificate"), WEIGHT("Weight certificate"), DRAFT("Draft / bill of exchange"),
		OTHER("Other document");

		private final String label;

		Kind(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

	}

	/** The app's document type this requirement is checked against, or null when it is only listed. */
	public DocType readAs() {
		return LcRequirements.readAs(kind);
	}

	/** "3 originals + 2 copies", "full set", "1 original" or "" when not stated. */
	public String countText() {
		StringBuilder text = new StringBuilder();
		if (fullSet) {
			text.append("full set").append(originals != null ? " (" + originals + "/" + originals + ")" : "");
		}
		else if (originals != null) {
			text.append(originals).append(originals == 1 ? " original" : " originals");
		}
		if (copies != null) {
			text.append(text.isEmpty() ? "" : " + ").append(copies).append(copies == 1 ? " copy" : " copies");
		}
		return text.toString();
	}

}
