package com.liabletech.amc.customer;

import java.util.Locale;
import java.util.Set;

/** Appliances a service business maintains, grouped into the families plans are sold for. */
public enum ApplianceKind {

	SPLIT_AC("Split AC", "AC"), WINDOW_AC("Window AC", "AC"), CASSETTE_AC("Cassette AC", "AC"),
	RO("RO water purifier", "RO"), WATER_HEATER("Water heater", "WATER_HEATER"), REFRIGERATOR("Refrigerator", "OTHER"),
	WASHING_MACHINE("Washing machine", "OTHER"), CHIMNEY("Kitchen chimney", "OTHER"), OTHER("Other appliance", "OTHER");

	private final String label;

	private final String family;

	ApplianceKind(String label, String family) {
		this.label = label;
		this.family = family;
	}

	public String label() {
		return label;
	}

	/** AC, RO, WATER_HEATER or OTHER: what an AMC plan is sold for. */
	public String family() {
		return family;
	}

	/** The kind as written by the AI or a person: "split AC", "RO_PURIFIER", "geyser". */
	public static ApplianceKind parse(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String text = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z]+", "_");
		for (ApplianceKind kind : values()) {
			if (kind.name().equals(text)) {
				return kind;
			}
		}
		Set<String> words = Set.of(text.split("_"));
		if (words.contains("SPLIT") || words.contains("INVERTER") || words.contains("HIGHWALL")) {
			return SPLIT_AC;
		}
		if (words.contains("WINDOW")) {
			return WINDOW_AC;
		}
		if (words.contains("CASSETTE")) {
			return CASSETTE_AC;
		}
		if (words.contains("RO") || words.contains("PURIFIER") || words.contains("UV") || words.contains("FILTER")) {
			return RO;
		}
		if (words.contains("GEYSER") || words.contains("HEATER")) {
			return WATER_HEATER;
		}
		if (words.contains("FRIDGE") || words.contains("REFRIGERATOR")) {
			return REFRIGERATOR;
		}
		if (words.contains("WASHING")) {
			return WASHING_MACHINE;
		}
		if (words.contains("CHIMNEY")) {
			return CHIMNEY;
		}
		if (words.contains("AC") || text.contains("AIR_CONDITIONER")) {
			return SPLIT_AC;
		}
		return OTHER;
	}

}
