package com.liabletech.parts.catalog;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/** A part the shop stocks: its number, what it is, its price to mechanics and how many are on the shelf. */
public record Part(Long id, Long accountId, String partNumber, String name, String category, String brand,
		String altNumbers, BigDecimal price, BigDecimal mrp, int stock, String rack, boolean active,
		OffsetDateTime createdAt, OffsetDateTime updatedAt) {

	public PartType type() {
		PartType type = PartType.parse(category);
		return type == null ? PartType.OTHER : type;
	}

	public boolean inStock() {
		return stock > 0;
	}

	/** The part's own number and the numbers it replaces. */
	public List<String> numbers() {
		List<String> alternates = altNumbers == null ? List.of()
				: Arrays.stream(altNumbers.split("[,;]")).map(String::trim).filter((s) -> !s.isEmpty()).toList();
		return java.util.stream.Stream.concat(java.util.stream.Stream.of(partNumber), alternates.stream()).toList();
	}

	/** "ProStop front brake pad set", for quotes. */
	public String describe() {
		return brand == null || brand.isBlank() || name.toLowerCase().startsWith(brand.toLowerCase()) ? name
				: brand + " " + Character.toLowerCase(name.charAt(0)) + name.substring(1);
	}

}
