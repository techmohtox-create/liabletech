package com.liabletech.platform.messaging;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/** A row in the outbox: one WhatsApp message or email, and what happened to it. */
public record OutboundMessage(Long id, Long accountId, String channel, String recipient, String subject, String body,
		String attachmentFileIds, String templateName, String status, String providerMessageId, String error,
		String relatedType, Long relatedId, OffsetDateTime createdAt, OffsetDateTime statusUpdatedAt) {

	public static final String SENT = "SENT";

	public static final String SIMULATED = "SIMULATED";

	public static final String FAILED = "FAILED";

	public boolean failed() {
		return FAILED.equals(status);
	}

	public boolean isWhatsApp() {
		return Outgoing.Channel.WHATSAPP.name().equals(channel);
	}

	public List<Long> attachmentIds() {
		if (attachmentFileIds == null || attachmentFileIds.isBlank()) {
			return List.of();
		}
		return Arrays.stream(attachmentFileIds.split(",")).map(String::trim).map(Long::valueOf).toList();
	}

}
