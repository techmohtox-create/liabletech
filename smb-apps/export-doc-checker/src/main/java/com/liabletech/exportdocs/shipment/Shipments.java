package com.liabletech.exportdocs.shipment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.text.Fuzzy;
import com.liabletech.platform.web.NotFoundException;

@Repository
public class Shipments {

	private final JdbcClient jdbc;

	public Shipments(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Shipment get(long accountId, long id) {
		return find(accountId, id).orElseThrow(() -> new NotFoundException("Shipment " + id));
	}

	public Optional<Shipment> find(long accountId, Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM shipment WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Shipment.class)
			.optional();
	}

	public long create(long accountId, String reference, String title, OffsetDateTime at) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO shipment (account_id, reference, title, status, created_at, updated_at)
				VALUES (?, ?, ?, 'COLLECTING', ?, ?)
				""").params(accountId, reference, title, at, at).update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void updateDetails(long accountId, long id, ShipmentForm form, OffsetDateTime at) {
		jdbc.sql("""
				UPDATE shipment SET title = ?, buyer_email = ?, forwarder_name = ?, forwarder_phone = ?, forwarder_email = ?,
				    planned_presentation = ?, notes = ?, updated_at = ?
				WHERE account_id = ? AND id = ?
				""")
			.params(blank(form.getTitle()), blank(form.getBuyerEmail()), blank(form.getForwarderName()),
					form.normalizedForwarderPhone(), blank(form.getForwarderEmail()), form.getPlannedPresentation(),
					blank(form.getNotes()), at, accountId, id)
			.update();
	}

	/** Copies the credit's main terms onto the shipment for lists and deadlines. */
	public void updateTerms(long id, String lcNumber, String issuingBank, String buyerName, String currency,
			BigDecimal amount, LocalDate expiry, LocalDate latestShipment, LocalDate shippedOn,
			LocalDate presentationDeadline) {
		jdbc.sql("""
				UPDATE shipment SET lc_number = ?, issuing_bank = ?, buyer_name = ?, currency = ?, lc_amount = ?,
				    lc_expiry = ?, latest_shipment = ?, shipped_on = ?, presentation_deadline = ?
				WHERE id = ?
				""")
			.params(cut(lcNumber, 60), cut(issuingBank, 200), cut(buyerName, 200), cut(currency, 3), amount, expiry,
					latestShipment, shippedOn, presentationDeadline, id)
			.update();
	}

	public void updateCheck(long id, String status, int critical, int warning, OffsetDateTime at) {
		jdbc.sql("""
				UPDATE shipment SET status = ?, critical_count = ?, warning_count = ?, checked_at = ?,
				    first_checked_at = COALESCE(first_checked_at, ?), updated_at = ?
				WHERE id = ?
				""").params(status, critical, warning, at, at, at, id).update();
	}

	public void setStatus(long accountId, long id, String status, LocalDate submittedOn, OffsetDateTime at) {
		jdbc.sql("UPDATE shipment SET status = ?, submitted_on = ?, updated_at = ? WHERE account_id = ? AND id = ?")
			.params(status, submittedOn, at, accountId, id)
			.update();
	}

	public void touch(long id, OffsetDateTime at) {
		jdbc.sql("UPDATE shipment SET updated_at = ? WHERE id = ?").params(at, id).update();
	}

	public List<Shipment> list(long accountId, boolean activeOnly, int limit) {
		return jdbc.sql("""
				SELECT * FROM shipment WHERE account_id = :accountId
				    AND (:activeOnly = FALSE OR status IN ('COLLECTING', 'CHECKED', 'READY'))
				ORDER BY updated_at DESC, id DESC LIMIT :limit
				""")
			.param("accountId", accountId)
			.param("activeOnly", activeOnly)
			.param("limit", limit)
			.query(Shipment.class)
			.list();
	}

	/** An active shipment under this LC number, as written by different people. */
	public Optional<Shipment> findByLcNumber(long accountId, String lcNumber) {
		if (lcNumber == null || lcNumber.isBlank()) {
			return Optional.empty();
		}
		return list(accountId, true, 200).stream()
			.filter((s) -> s.lcNumber() != null && Fuzzy.sameCode(s.lcNumber(), lcNumber))
			.findFirst();
	}

	public Optional<Shipment> findByReference(long accountId, String text) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		String code = Fuzzy.code(text);
		return list(accountId, true, 200).stream()
			.filter((s) -> code.contains(Fuzzy.code(s.reference()))
					|| (s.lcNumber() != null && Fuzzy.code(s.lcNumber()).length() >= 4 && code.contains(Fuzzy.code(s.lcNumber()))))
			.findFirst();
	}

	public long countFirstCheckedSince(long accountId, OffsetDateTime since) {
		return jdbc.sql("SELECT COUNT(*) FROM shipment WHERE account_id = ? AND first_checked_at >= ?")
			.params(accountId, since)
			.query(Long.class)
			.single();
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String cut(String value, int max) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}

}
