package com.liabletech.exportdocs.check;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/** Units of quantity on export documents, as written ("M/T", "KGS", "Nos.", "CTNS"). */
public enum QtyUnit {

	MT("MT", false), KG("KG", false), PCS("PCS", true), SETS("SETS", true), PAIRS("PAIRS", true),
	CARTONS("CARTONS", true), BAGS("BAGS", true), DOZ("DOZ", true), MTR("MTR", false), YDS("YDS", false),
	LTR("LTR", false), SQM("SQM", false), CBM("CBM", false);

	/** The written forms, as one regular-expression alternation, longest first. */
	static final String WRITTEN = "METRIC\\s+TONN?E?S|METRIC\\s+TONN?E?|M\\.\\s?T\\.?|M/T|MTS|MT|TONNES?|TONS?"
			+ "|KILOGRAMMES?|KILOGRAMS?|KILOS?|KGS?"
			+ "|PIECES|PIECE|PCS|PC|NOS|NUMBERS|UNITS?"
			+ "|SETS?|PAIRS?|PRS|CARTONS?|CTNS?|BAGS?|DOZENS?|DOZ|DZ"
			+ "|METRES?|METERS?|MTRS?|YARDS?|YDS?|LITRES?|LITERS?|LTRS?"
			+ "|SQ\\.?\\s?MTRS?|SQ\\.?\\s?METERS?|SQ\\.?\\s?METRES?|SQM|CBM";

	private static final Pattern WHOLE = Pattern.compile("^(?:" + WRITTEN + ")$");

	private final String label;

	private final boolean countable;

	QtyUnit(String label, boolean countable) {
		this.label = label;
		this.countable = countable;
	}

	public String label() {
		return label;
	}

	/**
	 * Counted in items or packing units. UCP 600 article 30(b) gives a 5% quantity tolerance only to
	 * goods that are not.
	 */
	public boolean countable() {
		return countable;
	}

	public static QtyUnit parse(String written) {
		if (written == null) {
			return null;
		}
		String text = written.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").replaceAll("[.,]+$", "");
		if (text.isEmpty() || !WHOLE.matcher(text).matches()) {
			return null;
		}
		String compact = text.replaceAll("[^A-Z/]", "");
		if (compact.startsWith("METRIC") || compact.equals("MT") || compact.equals("M/T") || compact.equals("MTS")
				|| compact.startsWith("TON")) {
			return MT;
		}
		if (compact.startsWith("KILO") || compact.startsWith("KG")) {
			return KG;
		}
		if (compact.startsWith("PIECE") || compact.startsWith("PC") || compact.equals("NOS") || compact.equals("NUMBERS")
				|| compact.startsWith("UNIT")) {
			return PCS;
		}
		if (compact.startsWith("SET")) {
			return SETS;
		}
		if (compact.startsWith("PAIR") || compact.equals("PRS")) {
			return PAIRS;
		}
		if (compact.startsWith("CARTON") || compact.startsWith("CTN")) {
			return CARTONS;
		}
		if (compact.startsWith("BAG")) {
			return BAGS;
		}
		if (compact.startsWith("DOZ") || compact.equals("DZ")) {
			return DOZ;
		}
		if (compact.startsWith("SQ")) {
			return SQM;
		}
		if (compact.startsWith("MET") || compact.startsWith("MTR")) {
			return MTR;
		}
		if (compact.startsWith("YARD") || compact.startsWith("YD")) {
			return YDS;
		}
		if (compact.startsWith("LIT") || compact.startsWith("LTR")) {
			return LTR;
		}
		if (compact.equals("CBM")) {
			return CBM;
		}
		return null;
	}

	/** A quantity in another unit, or null when the two units cannot be compared. */
	public static BigDecimal convert(BigDecimal quantity, QtyUnit from, QtyUnit to) {
		if (quantity == null || from == null || to == null) {
			return null;
		}
		if (from == to) {
			return quantity;
		}
		if (from == MT && to == KG) {
			return quantity.multiply(BigDecimal.valueOf(1000));
		}
		if (from == KG && to == MT) {
			return quantity.movePointLeft(3);
		}
		if (from == DOZ && to == PCS) {
			return quantity.multiply(BigDecimal.valueOf(12));
		}
		if (from == PCS && to == DOZ) {
			return quantity.divide(BigDecimal.valueOf(12), 6, java.math.RoundingMode.HALF_UP);
		}
		return null;
	}

}
