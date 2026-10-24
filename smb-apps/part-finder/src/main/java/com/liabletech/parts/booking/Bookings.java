package com.liabletech.parts.booking;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class Bookings {

	private final JdbcClient jdbc;

	public Bookings(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Booking get(long accountId, long id) {
		return jdbc.sql("SELECT * FROM booking WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Booking.class)
			.optional()
			.orElseThrow(() -> new NotFoundException("Booking " + id));
	}

	public long create(long accountId, Long enquiryId, long mechanicId, Long partId, String description, int quantity,
			BigDecimal price, OffsetDateTime at) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO booking (account_id, enquiry_id, mechanic_id, part_id, description, quantity, price, status,
				    created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, 'BOOKED', ?)
				""").params(accountId, enquiryId, mechanicId, partId, description, quantity, price, at).update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void finish(long accountId, long id, String status, String paymentMode, OffsetDateTime at) {
		jdbc.sql("UPDATE booking SET status = ?, payment_mode = ?, done_at = ? WHERE account_id = ? AND id = ?")
			.params(status, paymentMode, at, accountId, id)
			.update();
	}

	/** The mechanic's latest booking still waiting to be handed over. */
	public Optional<Booking> openFor(long mechanicId) {
		return jdbc.sql("SELECT * FROM booking WHERE mechanic_id = ? AND status = 'BOOKED' ORDER BY created_at DESC LIMIT 1")
			.param(mechanicId)
			.query(Booking.class)
			.optional();
	}

	public Optional<Booking> forEnquiry(long enquiryId) {
		return jdbc.sql("SELECT * FROM booking WHERE enquiry_id = ? ORDER BY id DESC LIMIT 1")
			.param(enquiryId)
			.query(Booking.class)
			.optional();
	}

	public List<BookingRow> list(long accountId, List<String> statuses, Long mechanicId, int limit) {
		return jdbc.sql("""
				SELECT b.*, m.name AS mechanic_name, m.garage, m.phone, p.part_number, p.rack, p.stock
				FROM booking b JOIN mechanic m ON m.id = b.mechanic_id LEFT JOIN part p ON p.id = b.part_id
				WHERE b.account_id = :accountId AND b.status IN (:statuses)
				    AND (CAST(:mechanicId AS BIGINT) IS NULL OR b.mechanic_id = :mechanicId)
				ORDER BY CASE WHEN b.status = 'BOOKED' THEN b.created_at END, b.created_at DESC
				LIMIT :limit
				""")
			.param("accountId", accountId)
			.param("statuses", statuses)
			.param("mechanicId", mechanicId)
			.param("limit", limit)
			.query(BookingRow.class)
			.list();
	}

	public record BookingRow(Long id, Long enquiryId, Long mechanicId, Long partId, String description, int quantity,
			BigDecimal price, String status, String paymentMode, OffsetDateTime createdAt, OffsetDateTime doneAt,
			String mechanicName, String garage, String phone, String partNumber, String rack, Integer stock) {

		public BigDecimal total() {
			return price.multiply(BigDecimal.valueOf(quantity));
		}

		public String mechanicDisplay() {
			return new com.liabletech.parts.mechanic.Mechanic(mechanicId, null, phone, mechanicName, garage, null, null,
					false, null).displayName();
		}

	}

}
