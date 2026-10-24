package com.liabletech.exportdocs.team;

import java.time.OffsetDateTime;

/** Someone who may send the business's export documents on WhatsApp: the owner, a documentation executive. */
public record DocSender(Long id, Long accountId, String name, String phone, boolean active, OffsetDateTime createdAt) {
}
