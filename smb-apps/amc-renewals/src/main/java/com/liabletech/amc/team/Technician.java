package com.liabletech.amc.team;

import java.time.OffsetDateTime;

/** A technician who sends appliance labels from customers' homes on WhatsApp. */
public record Technician(Long id, Long accountId, String name, String phone, boolean active, OffsetDateTime createdAt) {
}
