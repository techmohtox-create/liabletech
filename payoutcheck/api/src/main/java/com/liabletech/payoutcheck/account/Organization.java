package com.liabletech.payoutcheck.account;

import java.time.OffsetDateTime;

public record Organization(long id, String name, Plan plan, OffsetDateTime createdAt) {
}
