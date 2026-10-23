package com.liabletech.payoutcheck.audit;

import java.time.OffsetDateTime;

public record AuditEvent(long id, Long userId, String actorEmail, String action, String entity, Long entityId, String detail,
		OffsetDateTime createdAt) {
}
