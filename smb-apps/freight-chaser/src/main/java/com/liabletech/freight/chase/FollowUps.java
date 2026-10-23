package com.liabletech.freight.chase;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FollowUps {

	private final JdbcClient jdbc;

	public FollowUps(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void record(long accountId, long invoiceId, OffsetDateTime at, String kind, Integer stage, String note,
			Long outboundMessageId) {
		jdbc.sql("""
				INSERT INTO follow_up (account_id, invoice_id, happened_at, kind, stage, note, outbound_message_id)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""").params(accountId, invoiceId, at, kind, stage, note, outboundMessageId).update();
	}

	public List<FollowUp> forInvoice(long accountId, long invoiceId) {
		return jdbc.sql("SELECT * FROM follow_up WHERE account_id = ? AND invoice_id = ? ORDER BY happened_at DESC, id DESC")
			.params(accountId, invoiceId)
			.query(FollowUp.class)
			.list();
	}

	public long remindersSince(long accountId, OffsetDateTime since) {
		return jdbc.sql("SELECT COUNT(*) FROM follow_up WHERE account_id = ? AND kind = 'REMINDER' AND happened_at >= ?")
			.params(accountId, since)
			.query(Long.class)
			.single();
	}

}
