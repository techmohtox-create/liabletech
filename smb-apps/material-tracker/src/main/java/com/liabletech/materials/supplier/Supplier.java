package com.liabletech.materials.supplier;

import java.time.OffsetDateTime;

public record Supplier(Long id, Long accountId, String name, String phone, String email, String gstin,
		OffsetDateTime createdAt) {
}
