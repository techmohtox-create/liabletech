package com.liabletech.amc.customer;

import java.time.OffsetDateTime;

public record Customer(Long id, Long accountId, String name, String phone, String address, String area,
		boolean optedOut, String notes, OffsetDateTime createdAt) {

	public String displayName() {
		return name != null && !name.isBlank() ? name : phone;
	}

	/** The first name, for messages: "Namaste Sunita ji". */
	public String greetingName() {
		if (name == null || name.isBlank()) {
			return "";
		}
		return name.trim().split("\\s+")[0];
	}

}
