package com.liabletech.payoutcheck.common;

import java.util.Locale;

public enum Platform {

	SWIGGY("Swiggy"), ZOMATO("Zomato");

	private final String label;

	Platform(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}

	public static Platform parse(String value) {
		if (value == null) {
			throw new BadRequestException("Platform is required");
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new BadRequestException("Unknown platform: " + value);
		}
	}

}
