package com.liabletech.parts.mechanic;

import java.time.OffsetDateTime;

/** A mechanic or garage that buys from the shop and asks for parts on WhatsApp. */
public record Mechanic(Long id, Long accountId, String phone, String name, String garage, String area, String notes,
		boolean blocked, OffsetDateTime createdAt) {

	/** "Raju (Raju Motor Garage)", or the number when nothing else is known. */
	public String displayName() {
		boolean named = name != null && !name.isBlank();
		boolean hasGarage = garage != null && !garage.isBlank();
		if (named && hasGarage) {
			return name + " (" + garage + ")";
		}
		return named ? name : hasGarage ? garage : phone;
	}

	/** The first name, for replies: "Raju ji". */
	public String greetingName() {
		return name == null || name.isBlank() ? "" : name.trim().split("\\s+")[0];
	}

}
