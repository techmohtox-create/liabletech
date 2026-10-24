package com.liabletech.parts.enquiry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class Enquiries {

	private final JdbcClient jdbc;

	public Enquiries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Enquiry get(long accountId, long id) {
		return jdbc.sql("SELECT * FROM enquiry WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Enquiry.class)
			.optional()
			.orElseThrow(() -> new NotFoundException("Enquiry " + id));
	}

	public long create(long accountId, long mechanicId, Long inboundId, String kind, Long fileId, String text,
			String transcript, Identification found, OffsetDateTime at) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO enquiry (account_id, mechanic_id, inbound_id, kind, file_id, text, transcript, reading_status,
				    reading_note, part_type, part_name, part_numbers, vehicle_make, vehicle_model, vehicle_year, position,
				    quantity, confidence, question, status, received_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NEW', ?)
				""")
			.params(accountId, mechanicId, inboundId, kind, fileId, cut(text, 1000), cut(transcript, 2000), found.status(),
					cut(found.note(), 500), found.partType() == null ? null : found.partType().name(),
					cut(found.partName(), 150), numbers(found), cut(found.vehicleMake(), 40), cut(found.vehicleModel(), 60),
					found.vehicleYear(), cut(found.position(), 20), Math.max(1, Math.min(99, found.quantity())),
					confidence(found), cut(found.question(), 300), at)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	/** The counter's correction of what was asked for. */
	public void identify(long accountId, long id, Identification found) {
		jdbc.sql("""
				UPDATE enquiry SET reading_status = ?, part_type = ?, part_name = ?, part_numbers = ?, vehicle_make = ?,
				    vehicle_model = ?, vehicle_year = ?, position = ?, quantity = ?, question = ?
				WHERE account_id = ? AND id = ?
				""")
			.params(found.status(), found.partType() == null ? null : found.partType().name(), cut(found.partName(), 150),
					numbers(found), cut(found.vehicleMake(), 40), cut(found.vehicleModel(), 60), found.vehicleYear(),
					cut(found.position(), 20), Math.max(1, Math.min(99, found.quantity())), cut(found.question(), 300),
					accountId, id)
			.update();
	}

	public void addText(long accountId, long id, String text) {
		jdbc.sql("UPDATE enquiry SET text = ? WHERE account_id = ? AND id = ?").params(cut(text, 1000), accountId, id).update();
	}

	public void setStatus(long accountId, long id, String status, OffsetDateTime at) {
		boolean closing = !Enquiry.NEW.equals(status) && !Enquiry.QUOTED.equals(status);
		jdbc.sql("UPDATE enquiry SET status = ?, closed_at = CASE WHEN ? THEN ? ELSE NULL END WHERE account_id = ? AND id = ?")
			.params(status, closing, at, accountId, id)
			.update();
	}

	/** Records the first real answer (a quote, "not in stock", a question); later ones keep the first time. */
	public void answered(long accountId, long id, String by, OffsetDateTime at) {
		jdbc.sql("""
				UPDATE enquiry SET first_reply_at = COALESCE(first_reply_at, ?), replied_by = COALESCE(replied_by, ?)
				WHERE account_id = ? AND id = ?
				""").params(at, by, accountId, id).update();
	}

	public int closeQuotedBefore(OffsetDateTime quotedBefore, OffsetDateTime at) {
		return jdbc.sql("UPDATE enquiry SET status = 'CLOSED', closed_at = ? WHERE status = 'QUOTED' AND first_reply_at < ?")
			.params(at, quotedBefore)
			.update();
	}

	public void nudged(long id, OffsetDateTime at) {
		jdbc.sql("UPDATE enquiry SET nudged_at = ? WHERE id = ?").params(at, id).update();
	}

	/** The mechanic's latest enquiry with options waiting for an answer, from the last day. */
	public Optional<Enquiry> quotedFor(long mechanicId, OffsetDateTime since) {
		return jdbc.sql("""
				SELECT * FROM enquiry WHERE mechanic_id = ? AND status = 'QUOTED' AND received_at >= ?
				ORDER BY received_at DESC, id DESC LIMIT 1
				""").params(mechanicId, since).query(Enquiry.class).optional();
	}

	/** The mechanic's latest enquiry still waiting at the counter, from after the given time. */
	public Optional<Enquiry> waitingFrom(long mechanicId, OffsetDateTime since) {
		return jdbc.sql("""
				SELECT * FROM enquiry WHERE mechanic_id = ? AND status = 'NEW' AND received_at >= ?
				ORDER BY received_at DESC, id DESC LIMIT 1
				""").params(mechanicId, since).query(Enquiry.class).optional();
	}

	/** Enquiries nobody has answered for longer than allowed, not yet brought to the owner's notice. */
	public List<Enquiry> waitingLongerThan(OffsetDateTime receivedBefore, OffsetDateTime notBefore) {
		return jdbc.sql("""
				SELECT * FROM enquiry WHERE status = 'NEW' AND first_reply_at IS NULL AND nudged_at IS NULL
				    AND received_at < ? AND received_at >= ?
				ORDER BY received_at
				""").params(receivedBefore, notBefore).query(Enquiry.class).list();
	}

	public List<EnquiryRow> list(long accountId, List<String> statuses, Long mechanicId, int limit) {
		return jdbc.sql("""
				SELECT e.*, m.name AS mechanic_name, m.garage, m.phone, m.area,
				    (SELECT COUNT(*) FROM quote_option q WHERE q.enquiry_id = e.id) AS options
				FROM enquiry e JOIN mechanic m ON m.id = e.mechanic_id
				WHERE e.account_id = :accountId AND e.status IN (:statuses)
				    AND (CAST(:mechanicId AS BIGINT) IS NULL OR e.mechanic_id = :mechanicId)
				ORDER BY CASE WHEN e.status = 'NEW' THEN e.received_at END, e.received_at DESC, e.id DESC
				LIMIT :limit
				""")
			.param("accountId", accountId)
			.param("statuses", statuses)
			.param("mechanicId", mechanicId)
			.param("limit", limit)
			.query(EnquiryRow.class)
			.list();
	}

	private static String numbers(Identification found) {
		return found.partNumbers().isEmpty() ? null : cut(String.join(", ", found.partNumbers()), 300);
	}

	private static BigDecimal confidence(Identification found) {
		return BigDecimal.valueOf(Math.max(0, Math.min(1, found.confidence()))).setScale(2, RoundingMode.HALF_UP);
	}

	private static String cut(String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}

	/** An enquiry with its mechanic, for the counter's lists. */
	public record EnquiryRow(Long id, Long mechanicId, String kind, Long fileId, String text, String transcript,
			String readingStatus, String partType, String partName, String partNumbers, String vehicleMake,
			String vehicleModel, Integer vehicleYear, String position, int quantity, BigDecimal confidence, String question,
			String status, OffsetDateTime receivedAt, OffsetDateTime firstReplyAt, String repliedBy, String mechanicName,
			String garage, String phone, String area, long options) {

		public String mechanicDisplay() {
			return new com.liabletech.parts.mechanic.Mechanic(mechanicId, null, phone, mechanicName, garage, area, null,
					false, null).displayName();
		}

		public String describe() {
			return toEnquiry().identification().describe();
		}

		public Long responseSeconds() {
			return toEnquiry().responseSeconds();
		}

		/** When it arrived, for the counter's running timers. */
		public String receivedIso() {
			return toEnquiry().receivedIso();
		}

		private Enquiry toEnquiry() {
			return new Enquiry(id, null, mechanicId, null, kind, fileId, text, transcript, readingStatus, null, partType,
					partName, partNumbers, vehicleMake, vehicleModel, vehicleYear, position, quantity, confidence, question,
					status, receivedAt, firstReplyAt, repliedBy, null, null);
		}

	}

}
