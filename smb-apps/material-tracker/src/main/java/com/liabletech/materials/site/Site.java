package com.liabletech.materials.site;

import java.time.OffsetDateTime;

/** A construction site. Each active site is billed on the plan. */
public record Site(Long id, Long accountId, String name, String location, boolean active, OffsetDateTime createdAt) {
}
