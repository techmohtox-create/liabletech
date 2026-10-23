package com.liabletech.platform.messaging;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class Outbox {

	private final JdbcClient jdbc;

	public Outbox(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	long record(Outgoing message, String status, String providerMessageId, String error, OffsetDateTime at) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO outbound_message (account_id, channel, recipient, subject, body, attachment_file_ids,
				    template_name, status, provider_message_id, error, related_type, related_id, created_at,
				    status_updated_at)
				VALUES (:accountId, :channel, :recipient, :subject, :body, :attachments, :template, :status,
				    :providerId, :error, :relatedType, :relatedId, :at, :at)
				""")
			.param("accountId", message.accountId())
			.param("channel", message.channel().name())
			.param("recipient", message.to())
			.param("subject", truncate(message.subject(), 300))
			.param("body", truncate(message.body(), 8000))
			.param("attachments",
					message.attachments().isEmpty() ? null
							: message.attachments()
								.stream()
								.map((a) -> String.valueOf(a.fileId()))
								.collect(Collectors.joining(",")))
			.param("template", message.template() == null ? null : message.template().name())
			.param("status", status)
			.param("providerId", providerMessageId)
			.param("error", truncate(error, 1000))
			.param("relatedType", message.relatedType())
			.param("relatedId", message.relatedId())
			.param("at", at)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public OutboundMessage get(long id) {
		return jdbc.sql("SELECT * FROM outbound_message WHERE id = ?").param(id).query(OutboundMessage.class).single();
	}

	public List<OutboundMessage> forAccount(long accountId, String channel, int limit) {
		return jdbc.sql("""
				SELECT * FROM outbound_message WHERE account_id = :accountId
				    AND (CAST(:channel AS VARCHAR(10)) IS NULL OR channel = :channel)
				ORDER BY created_at DESC, id DESC LIMIT :limit
				""")
			.param("accountId", accountId)
			.param("channel", channel)
			.param("limit", limit)
			.query(OutboundMessage.class)
			.list();
	}

	/** WhatsApp messages sent to one phone number, oldest first. */
	public List<OutboundMessage> whatsAppTo(long accountId, String phone, int limit) {
		List<OutboundMessage> latest = jdbc.sql("""
				SELECT * FROM outbound_message WHERE account_id = ? AND channel = 'WHATSAPP' AND recipient = ?
				ORDER BY created_at DESC, id DESC LIMIT ?
				""").params(accountId, phone, limit).query(OutboundMessage.class).list();
		return latest.reversed();
	}

	/** WhatsApp messages to one phone, including replies sent without an account (unknown senders). */
	public List<OutboundMessage> whatsAppToPhone(long accountId, String phone, int limit) {
		return jdbc.sql("""
				SELECT * FROM outbound_message WHERE channel = 'WHATSAPP' AND recipient = ?
				    AND (account_id = ? OR account_id IS NULL)
				ORDER BY created_at DESC, id DESC LIMIT ?
				""").params(phone, accountId, limit).query(OutboundMessage.class).list();
	}

	public List<OutboundMessage> about(long accountId, String relatedType, long relatedId) {
		return jdbc.sql("""
				SELECT * FROM outbound_message WHERE account_id = ? AND related_type = ? AND related_id = ?
				ORDER BY created_at DESC, id DESC
				""").params(accountId, relatedType, relatedId).query(OutboundMessage.class).list();
	}

	/** Delivery receipts from WhatsApp: delivered, read, failed. */
	public void updateDeliveryStatus(String providerMessageId, String status, String error, OffsetDateTime at) {
		jdbc.sql("""
				UPDATE outbound_message SET status = ?, error = COALESCE(?, error), status_updated_at = ?
				WHERE provider_message_id = ? AND status <> 'FAILED'
				""").params(status, truncate(error, 1000), at, providerMessageId).update();
	}

	public long countSince(long accountId, OffsetDateTime since) {
		return jdbc.sql("SELECT COUNT(*) FROM outbound_message WHERE account_id = ? AND created_at >= ?")
			.params(accountId, since)
			.query(Long.class)
			.single();
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
	}

}
