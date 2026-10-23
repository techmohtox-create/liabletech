package com.liabletech.payoutcheck.account;

import java.time.OffsetDateTime;

public record AppUser(long id, long orgId, String email, String fullName, String passwordHash, Role role, boolean active,
		OffsetDateTime createdAt, OffsetDateTime lastLoginAt) {
}
