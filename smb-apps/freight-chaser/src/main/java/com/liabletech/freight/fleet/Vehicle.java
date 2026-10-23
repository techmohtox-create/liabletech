package com.liabletech.freight.fleet;

import java.util.Locale;

public record Vehicle(Long id, Long accountId, String registration, String description, boolean active) {

	/** Registration numbers are stored without spaces or dashes: "MH 12 AB-1234" becomes "MH12AB1234". */
	public static String normalize(String registration) {
		return registration == null ? null : registration.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
	}

	public String label() {
		return description == null || description.isBlank() ? registration : registration + " · " + description;
	}

}
