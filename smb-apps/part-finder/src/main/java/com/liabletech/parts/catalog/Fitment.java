package com.liabletech.parts.catalog;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A vehicle a part fits. Years are inclusive and either may be open. */
public record Fitment(Long id, Long partId, String make, String model, Integer yearFrom, Integer yearTo,
		String variant) {

	public boolean fits(String otherModel, Integer year) {
		if (!VehicleCatalog.sameModel(model, otherModel)) {
			return false;
		}
		return year == null || ((yearFrom == null || year >= yearFrom) && (yearTo == null || year <= yearTo));
	}

	/** "Swift 2018–2024", "Bolero (diesel)", "Activa". */
	public String describe() {
		StringBuilder text = new StringBuilder(model);
		if (yearFrom != null || yearTo != null) {
			text.append(' ')
				.append(yearFrom == null ? "up to " + yearTo : yearTo == null ? yearFrom + " on" : yearFrom + "–" + yearTo);
		}
		if (variant != null && !variant.isBlank()) {
			text.append(" (").append(variant).append(')');
		}
		return text.toString();
	}

	private static final Pattern RANGE = Pattern
		.compile("((?:19|20)\\d{2})?\\s*(?:-|–|\\bto\\b)\\s*((?:19|20)\\d{2})?\\s*$");

	private static final Pattern ONWARDS = Pattern.compile("((?:19|20)\\d{2})\\s*(?:on|onwards|\\+)\\s*$");

	private static final Pattern SINGLE = Pattern.compile("\\b((?:19|20)\\d{2})\\s*$");

	private static final Pattern VARIANT = Pattern.compile("\\(([^)]*)\\)");

	/**
	 * Reads how a stock list writes what a part fits: "Swift 2018-2024", "Maruti Dzire 2017-",
	 * "Bolero 2012 on", "Bolero (diesel)", "Activa". Unknown models are kept as written.
	 */
	public static Fitment parse(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String rest = text.trim();
		String variant = null;
		Matcher bracket = VARIANT.matcher(rest);
		if (bracket.find()) {
			variant = bracket.group(1).trim();
			rest = (rest.substring(0, bracket.start()) + " " + rest.substring(bracket.end())).trim();
		}
		Integer from = null;
		Integer to = null;
		Matcher range = RANGE.matcher(rest);
		Matcher onwards = ONWARDS.matcher(rest);
		Matcher single = SINGLE.matcher(rest);
		if (range.find() && (range.group(1) != null || range.group(2) != null)) {
			from = year(range.group(1));
			to = year(range.group(2));
			rest = rest.substring(0, range.start()).trim();
		}
		else if (onwards.find()) {
			from = year(onwards.group(1));
			rest = rest.substring(0, onwards.start()).trim();
		}
		else if (single.find()) {
			from = year(single.group(1));
			to = from;
			rest = rest.substring(0, single.start()).trim();
		}
		if (rest.isEmpty()) {
			return null;
		}
		Optional<VehicleCatalog.Vehicle> vehicle = VehicleCatalog.find(rest);
		return new Fitment(null, null, vehicle.map(VehicleCatalog.Vehicle::make).orElse(null),
				vehicle.map(VehicleCatalog.Vehicle::model).orElse(rest), from, to,
				variant == null || variant.isEmpty() ? null : variant);
	}

	private static Integer year(String digits) {
		return digits == null ? null : Integer.valueOf(digits);
	}

}
