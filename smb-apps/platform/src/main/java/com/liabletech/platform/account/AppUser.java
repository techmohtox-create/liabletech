package com.liabletech.platform.account;

import java.time.OffsetDateTime;

/** Someone who signs in to the web app. Drivers, supervisors and technicians use WhatsApp only. */
public record AppUser(Long id, Long accountId, String name, String email, String passwordHash, String role,
		boolean active, OffsetDateTime createdAt) {

	public static final String OWNER = "OWNER";

	public static final String STAFF = "STAFF";

	public boolean isOwner() {
		return OWNER.equals(role);
	}

}
