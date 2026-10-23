package com.liabletech.platform.whatsapp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class InboundMessages {

	private final JdbcClient jdbc;

	public InboundMessages(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Stores a message once. WhatsApp retries webhooks it thinks failed, so a message id seen before
	 * returns empty and is not processed again.
	 */
	public Optional<Long> insertIfNew(NewInbound message) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		try {
			jdbc.sql("""
					INSERT INTO wa_inbound (account_id, wa_message_id, from_phone, to_phone_number_id, contact_name,
					    message_type, text_body, media_id, media_mime_type, media_filename, file_id,
					    context_wa_message_id, received_at, status)
					VALUES (:accountId, :waMessageId, :fromPhone, :toPhoneNumberId, :contactName, :messageType,
					    :textBody, :mediaId, :mediaMimeType, :mediaFilename, :fileId, :contextWaMessageId,
					    :receivedAt, 'RECEIVED')
					""")
				.param("accountId", message.accountId())
				.param("waMessageId", message.waMessageId())
				.param("fromPhone", message.fromPhone())
				.param("toPhoneNumberId", message.toPhoneNumberId())
				.param("contactName", truncate(message.contactName(), 100))
				.param("messageType", message.messageType())
				.param("textBody", truncate(message.textBody(), 4000))
				.param("mediaId", message.mediaId())
				.param("mediaMimeType", message.mediaMimeType())
				.param("mediaFilename", truncate(message.mediaFilename(), 255))
				.param("fileId", message.fileId())
				.param("contextWaMessageId", message.contextWaMessageId())
				.param("receivedAt", message.receivedAt())
				.update(keys, "id");
			return Optional.of(keys.getKeyAs(Long.class));
		}
		catch (DuplicateKeyException ex) {
			return Optional.empty();
		}
	}

	public Optional<InboundMessage> find(long id) {
		return jdbc.sql("SELECT * FROM wa_inbound WHERE id = ?").param(id).query(InboundMessage.class).optional();
	}

	public void assignAccount(long id, long accountId) {
		jdbc.sql("UPDATE wa_inbound SET account_id = ? WHERE id = ?").params(accountId, id).update();
	}

	public void attachFile(long id, long fileId) {
		jdbc.sql("UPDATE wa_inbound SET file_id = ? WHERE id = ?").params(fileId, id).update();
	}

	public void finish(long id, String status, String error, OffsetDateTime at) {
		jdbc.sql("UPDATE wa_inbound SET status = ?, error = ?, processed_at = ?, attempts = attempts + 1 WHERE id = ?")
			.params(status, truncate(error, 1000), at, id)
			.update();
	}

	/** Messages to pick up again: never finished (the app restarted mid-way) or failed fewer than 3 times. */
	public List<Long> needingRetry(OffsetDateTime receivedBefore) {
		return jdbc.sql("""
				SELECT id FROM wa_inbound
				WHERE (status = 'RECEIVED' AND received_at < ?) OR (status = 'FAILED' AND attempts < 3)
				ORDER BY received_at
				""").param(receivedBefore).query(Long.class).list();
	}

	/** The latest messages from one phone number to one account, oldest first. */
	public List<InboundMessage> recentFrom(long accountId, String phone, int limit) {
		List<InboundMessage> latest = jdbc.sql("""
				SELECT * FROM wa_inbound WHERE account_id = ? AND from_phone = ?
				ORDER BY received_at DESC, id DESC LIMIT ?
				""").params(accountId, phone, limit).query(InboundMessage.class).list();
		return latest.reversed();
	}

	/** Messages from one phone to this account, including ones not yet routed to any account. */
	public List<InboundMessage> recentFromPhone(long accountId, String phone, int limit) {
		List<InboundMessage> latest = jdbc.sql("""
				SELECT * FROM wa_inbound WHERE from_phone = ? AND (account_id = ? OR account_id IS NULL)
				ORDER BY received_at DESC, id DESC LIMIT ?
				""").params(phone, accountId, limit).query(InboundMessage.class).list();
		return latest.reversed();
	}

	public List<InboundMessage> recentForAccount(long accountId, int limit) {
		return jdbc.sql("SELECT * FROM wa_inbound WHERE account_id = ? ORDER BY received_at DESC, id DESC LIMIT ?")
			.params(accountId, limit)
			.query(InboundMessage.class)
			.list();
	}

	static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	public record NewInbound(Long accountId, String waMessageId, String fromPhone, String toPhoneNumberId,
			String contactName, String messageType, String textBody, String mediaId, String mediaMimeType,
			String mediaFilename, Long fileId, String contextWaMessageId, OffsetDateTime receivedAt) {
	}

}
