package com.liabletech.materials.catalog;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * Units on Indian site paperwork. Quantities are compared after converting to the material's own
 * unit: a bill in MT against slips in kg, sand in brass against slips in cft, steel billed by
 * weight against bars counted at the gate.
 */
public enum Unit {

	BAG("bag"), KG("kg"), MT("MT"), CFT("cft"), BRASS("brass"), CUM("m³"), NOS("nos"), LTR("litre"), SQFT("sq ft"),
	RMT("running m");

	/** One brass is 100 cubic feet. */
	static final BigDecimal CFT_PER_BRASS = BigDecimal.valueOf(100);

	static final BigDecimal CFT_PER_CUM = new BigDecimal("35.3147");

	/** Standard length of a TMT bar. */
	static final BigDecimal BAR_LENGTH_M = BigDecimal.valueOf(12);

	private static final Map<String, Unit> SPELLINGS = Map.ofEntries(Map.entry("BAG", BAG), Map.entry("BAGS", BAG),
			Map.entry("BG", BAG), Map.entry("KG", KG), Map.entry("KGS", KG), Map.entry("KILO", KG), Map.entry("MT", MT),
			Map.entry("TON", MT), Map.entry("TONS", MT), Map.entry("TONNE", MT), Map.entry("TONNES", MT),
			Map.entry("T", MT), Map.entry("CFT", CFT), Map.entry("CUFT", CFT), Map.entry("FT3", CFT),
			Map.entry("BRASS", BRASS), Map.entry("CUM", CUM), Map.entry("M3", CUM), Map.entry("CUBICMETER", CUM),
			Map.entry("CUBICMETRE", CUM), Map.entry("NOS", NOS), Map.entry("NO", NOS), Map.entry("PCS", NOS),
			Map.entry("PC", NOS), Map.entry("PIECES", NOS), Map.entry("NUMBERS", NOS), Map.entry("LTR", LTR),
			Map.entry("LITRE", LTR), Map.entry("LITER", LTR), Map.entry("L", LTR), Map.entry("SQFT", SQFT),
			Map.entry("RMT", RMT));

	private final String label;

	Unit(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	/** The unit after a quantity: "1 bag", "200 bags". */
	public String label(BigDecimal quantity) {
		boolean one = quantity != null && quantity.compareTo(BigDecimal.ONE) == 0;
		return switch (this) {
			case BAG -> one ? "bag" : "bags";
			case LTR -> one ? "litre" : "litres";
			default -> label;
		};
	}

	/** Reads the unit as written on a slip ("Bags", "M.T.", "Cu.M", "Nos."); null if unknown. */
	public static Unit parse(String text) {
		if (text == null) {
			return null;
		}
		String key = text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
		return SPELLINGS.get(key);
	}

	/**
	 * Converts a quantity into another unit for a material, or returns null when the two cannot be
	 * compared (litres against bags, or bars counted for a material with no bar diameter).
	 */
	public static BigDecimal convert(BigDecimal quantity, Unit from, Unit to, Material material) {
		if (quantity == null || from == null || to == null) {
			return null;
		}
		if (from == to) {
			return quantity;
		}
		BigDecimal kg = toKg(quantity, from, material);
		if (kg != null) {
			return fromKg(kg, to, material);
		}
		BigDecimal cft = toCft(quantity, from);
		if (cft != null) {
			return fromCft(cft, to);
		}
		return null;
	}

	private static BigDecimal toKg(BigDecimal quantity, Unit unit, Material material) {
		return switch (unit) {
			case KG -> quantity;
			case MT -> quantity.multiply(BigDecimal.valueOf(1000));
			case BAG -> material != null && material.bagKg() != null ? quantity.multiply(material.bagKg()) : null;
			case NOS -> material != null && material.barDiameterMm() != null
					? quantity.multiply(barKg(material.barDiameterMm())) : null;
			default -> null;
		};
	}

	private static BigDecimal fromKg(BigDecimal kg, Unit unit, Material material) {
		MathContext precision = MathContext.DECIMAL64;
		return switch (unit) {
			case KG -> kg;
			case MT -> kg.divide(BigDecimal.valueOf(1000), precision);
			case BAG -> material != null && material.bagKg() != null ? kg.divide(material.bagKg(), precision) : null;
			case NOS -> material != null && material.barDiameterMm() != null
					? kg.divide(barKg(material.barDiameterMm()), precision) : null;
			default -> null;
		};
	}

	private static BigDecimal toCft(BigDecimal quantity, Unit unit) {
		return switch (unit) {
			case CFT -> quantity;
			case BRASS -> quantity.multiply(CFT_PER_BRASS);
			case CUM -> quantity.multiply(CFT_PER_CUM);
			default -> null;
		};
	}

	private static BigDecimal fromCft(BigDecimal cft, Unit unit) {
		return switch (unit) {
			case CFT -> cft;
			case BRASS -> cft.divide(CFT_PER_BRASS, MathContext.DECIMAL64);
			case CUM -> cft.divide(CFT_PER_CUM, MathContext.DECIMAL64);
			default -> null;
		};
	}

	/**
	 * Theoretical weight of one 12 m TMT bar: D²/162 kg per metre, the formula site engineers use.
	 * A 12 mm bar weighs 0.889 kg/m, so 10.67 kg per bar.
	 */
	public static BigDecimal barKg(int diameterMm) {
		return BigDecimal.valueOf((long) diameterMm * diameterMm)
			.divide(BigDecimal.valueOf(162), 6, RoundingMode.HALF_UP)
			.multiply(BAR_LENGTH_M);
	}

}
