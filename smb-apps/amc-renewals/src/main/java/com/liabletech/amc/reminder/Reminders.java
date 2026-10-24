package com.liabletech.amc.reminder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class Reminders {

	public static final String RENEWAL = "RENEWAL";

	public static final String SERVICE = "SERVICE";

	private final JdbcClient jdbc;

	public Reminders(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** The offsets (or visit numbers) already sent for a contract. */
	public Set<Integer> sent(long contractId, String kind) {
		return jdbc.sql("SELECT offset_days FROM reminder WHERE contract_id = ? AND kind = ?")
			.params(contractId, kind)
			.query(Integer.class)
			.list()
			.stream()
			.collect(Collectors.toSet());
	}

	/** Records a reminder; sending the same one again (a second "Remind now" the same day) updates it. */
	public void record(long accountId, long contractId, String kind, int offset, LocalDate dueOn, OffsetDateTime sentAt,
			Long outboundId) {
		int updated = jdbc.sql("""
				UPDATE reminder SET sent_at = ?, outbound_id = ? WHERE contract_id = ? AND kind = ? AND offset_days = ?
				""").params(sentAt, outboundId, contractId, kind, offset).update();
		if (updated == 0) {
			jdbc.sql("""
					INSERT INTO reminder (account_id, contract_id, kind, offset_days, due_on, sent_at, outbound_id)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					""").params(accountId, contractId, kind, offset, dueOn, sentAt, outboundId).update();
		}
	}

	public long countSince(long accountId, OffsetDateTime since) {
		return jdbc.sql("SELECT COUNT(*) FROM reminder WHERE account_id = ? AND sent_at >= ?")
			.params(accountId, since)
			.query(Long.class)
			.single();
	}

	public List<SentReminder> recent(long accountId, int limit) {
		return jdbc.sql("""
				SELECT r.id, r.contract_id, r.kind, r.offset_days, r.due_on, r.sent_at, c.id AS customer_id,
				    c.name AS customer_name, c.phone, k.ends_on, k.status AS contract_status
				FROM reminder r JOIN contract k ON k.id = r.contract_id JOIN customer c ON c.id = k.customer_id
				WHERE r.account_id = ? ORDER BY r.sent_at DESC, r.id DESC LIMIT ?
				""").params(accountId, limit).query(SentReminder.class).list();
	}

	public List<SentReminder> forContract(long contractId) {
		return jdbc.sql("""
				SELECT r.id, r.contract_id, r.kind, r.offset_days, r.due_on, r.sent_at, c.id AS customer_id,
				    c.name AS customer_name, c.phone, k.ends_on, k.status AS contract_status
				FROM reminder r JOIN contract k ON k.id = r.contract_id JOIN customer c ON c.id = k.customer_id
				WHERE r.contract_id = ? ORDER BY r.sent_at
				""").param(contractId).query(SentReminder.class).list();
	}

	public record SentReminder(Long id, Long contractId, String kind, int offsetDays, LocalDate dueOn,
			OffsetDateTime sentAt, Long customerId, String customerName, String phone, LocalDate endsOn,
			String contractStatus) {

		/** "Renewal reminder, 15 days before the end", "Reminder for service visit 2". */
		public String describe() {
			if (SERVICE.equals(kind)) {
				return "Reminder for service visit " + offsetDays;
			}
			if (offsetDays == 0) {
				return "Renewal reminder, on the end date";
			}
			int days = Math.abs(offsetDays);
			return "Renewal reminder, " + days + (days == 1 ? " day " : " days ") + (offsetDays < 0 ? "before the end" : "after the end");
		}

	}

}
