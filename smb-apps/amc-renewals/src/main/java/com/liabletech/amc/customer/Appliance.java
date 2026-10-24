package com.liabletech.amc.customer;

import java.time.OffsetDateTime;

/** One appliance at a customer's home, as read off its rating label. */
public record Appliance(Long id, Long accountId, Long customerId, String kind, String brand, String model,
		String serialNumber, String capacity, String refrigerant, String manufactured, String location, Long labelFileId,
		OffsetDateTime createdAt) {

	public ApplianceKind applianceKind() {
		ApplianceKind parsed = ApplianceKind.parse(kind);
		return parsed == null ? ApplianceKind.OTHER : parsed;
	}

	/** "LG 1.5 TR split AC", for messages and lists. */
	public String describe() {
		StringBuilder text = new StringBuilder();
		if (brand != null && !brand.isBlank()) {
			text.append(brand.trim()).append(' ');
		}
		if (capacity != null && !capacity.isBlank()) {
			text.append(capacity.trim()).append(' ');
		}
		String label = applianceKind().label();
		boolean acronym = label.length() > 1 && Character.isUpperCase(label.charAt(1));
		text.append(text.isEmpty() || acronym ? label : Character.toLowerCase(label.charAt(0)) + label.substring(1));
		if (location != null && !location.isBlank()) {
			text.append(" (").append(location.trim()).append(')');
		}
		return text.toString();
	}

}
