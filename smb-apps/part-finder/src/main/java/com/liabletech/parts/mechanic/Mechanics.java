package com.liabletech.parts.mechanic;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class Mechanics {

	private final JdbcClient jdbc;

	public Mechanics(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Mechanic get(long accountId, long id) {
		return jdbc.sql("SELECT * FROM mechanic WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Mechanic.class)
			.optional()
			.orElseThrow(() -> new NotFoundException("Mechanic " + id));
	}

	public Optional<Mechanic> byPhone(long accountId, String phone) {
		if (phone == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM mechanic WHERE account_id = ? AND phone = ?")
			.params(accountId, phone)
			.query(Mechanic.class)
			.optional();
	}

	/**
	 * Shops this number buys from, the one it last asked first: for messages on the shared number. A
	 * shop that blocked the number still gets its messages, and ignores them.
	 */
	public List<Long> accountsFor(String phone) {
		return jdbc.sql("""
				SELECT m.account_id FROM mechanic m
				LEFT JOIN (SELECT mechanic_id, MAX(received_at) AS last_asked FROM enquiry GROUP BY mechanic_id) e
				    ON e.mechanic_id = m.id
				WHERE m.phone = ?
				ORDER BY e.last_asked DESC NULLS LAST, m.id DESC
				""").param(phone).query(Long.class).list();
	}

	/** The mechanic with this number, added if new; a WhatsApp profile name fills a missing name. */
	public Mechanic findOrCreate(long accountId, String phone, String name, OffsetDateTime at) {
		Optional<Mechanic> existing = byPhone(accountId, phone);
		if (existing.isPresent()) {
			Mechanic mechanic = existing.get();
			if ((mechanic.name() == null || mechanic.name().isBlank()) && name != null && !name.isBlank()) {
				jdbc.sql("UPDATE mechanic SET name = ? WHERE id = ?").params(name.trim(), mechanic.id()).update();
				return get(accountId, mechanic.id());
			}
			return mechanic;
		}
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("INSERT INTO mechanic (account_id, phone, name, created_at) VALUES (?, ?, ?, ?)")
			.params(accountId, phone, name == null || name.isBlank() ? null : name.trim(), at)
			.update(keys, "id");
		return get(accountId, keys.getKeyAs(Long.class));
	}

	public void update(long accountId, long id, String name, String garage, String area, String notes) {
		jdbc.sql("UPDATE mechanic SET name = ?, garage = ?, area = ?, notes = ? WHERE account_id = ? AND id = ?")
			.params(blank(name), blank(garage), blank(area), blank(notes), accountId, id)
			.update();
	}

	public void setBlocked(long accountId, long id, boolean blocked) {
		jdbc.sql("UPDATE mechanic SET blocked = ? WHERE account_id = ? AND id = ?").params(blocked, accountId, id).update();
	}

	public List<MechanicRow> list(long accountId, String search, OffsetDateTime since, int limit) {
		String like = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
		return jdbc.sql("""
				SELECT m.id, m.phone, m.name, m.garage, m.area, m.blocked,
				    (SELECT COUNT(*) FROM enquiry e WHERE e.mechanic_id = m.id AND e.received_at >= :since) AS enquiries,
				    (SELECT COUNT(*) FROM booking b WHERE b.mechanic_id = m.id AND b.status <> 'CANCELLED'
				        AND b.created_at >= :since) AS bookings,
				    (SELECT COALESCE(SUM(b.price * b.quantity), 0) FROM booking b WHERE b.mechanic_id = m.id
				        AND b.status <> 'CANCELLED' AND b.created_at >= :since) AS booked_value,
				    (SELECT MAX(e.received_at) FROM enquiry e WHERE e.mechanic_id = m.id) AS last_enquiry
				FROM mechanic m
				WHERE m.account_id = :accountId
				    AND (CAST(:search AS VARCHAR(200)) IS NULL OR LOWER(COALESCE(m.name, '')) LIKE :search
				        OR LOWER(COALESCE(m.garage, '')) LIKE :search OR m.phone LIKE :search)
				ORDER BY last_enquiry DESC NULLS LAST, m.name
				LIMIT :limit
				""")
			.param("accountId", accountId)
			.param("since", since)
			.param("search", like)
			.param("limit", limit)
			.query(MechanicRow.class)
			.list();
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public record MechanicRow(Long id, String phone, String name, String garage, String area, boolean blocked,
			long enquiries, long bookings, java.math.BigDecimal bookedValue, OffsetDateTime lastEnquiry) {

		public String displayName() {
			return new Mechanic(id, null, phone, name, garage, area, null, blocked, null).displayName();
		}

	}

}
