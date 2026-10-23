package com.liabletech.freight.trip;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.text.Fuzzy;
import com.liabletech.platform.web.NotFoundException;

@Repository
public class Trips {

	private final JdbcClient jdbc;

	public Trips(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Trip get(long accountId, long id) {
		return find(accountId, id).orElseThrow(() -> new NotFoundException("Trip " + id));
	}

	public Optional<Trip> find(long accountId, Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM trip WHERE account_id = ? AND id = ?").params(accountId, id).query(Trip.class).optional();
	}

	/** Trips with a row each for the list page, newest first, optionally of one status. */
	public List<TripRow> list(long accountId, String status, int limit) {
		return jdbc.sql("""
				SELECT t.*, p.name AS party_name, v.registration AS vehicle,
				    (SELECT COUNT(*) FROM pod WHERE pod.trip_id = t.id AND pod.review_status <> 'REJECTED') AS pods
				FROM trip t
				LEFT JOIN party p ON p.id = t.party_id
				LEFT JOIN vehicle v ON v.id = t.vehicle_id
				WHERE t.account_id = :accountId AND (CAST(:status AS VARCHAR(20)) IS NULL OR t.status = :status)
				ORDER BY COALESCE(t.lr_date, CAST(t.created_at AS DATE)) DESC, t.id DESC
				LIMIT :limit
				""")
			.param("accountId", accountId)
			.param("status", status)
			.param("limit", limit)
			.query(TripRow.class)
			.list();
	}

	public List<Trip> ofIds(long accountId, List<Long> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("SELECT * FROM trip WHERE account_id = :accountId AND id IN (:ids) ORDER BY lr_date, id")
			.param("accountId", accountId)
			.param("ids", ids)
			.query(Trip.class)
			.list();
	}

	public List<Trip> ofInvoice(long accountId, long invoiceId) {
		return jdbc.sql("SELECT * FROM trip WHERE account_id = ? AND invoice_id = ? ORDER BY lr_date, id")
			.params(accountId, invoiceId)
			.query(Trip.class)
			.list();
	}

	/**
	 * Trips whose LR number matches one read off a photo. LR numbers are matched loosely
	 * ("LR-04521" is 4521) because drivers' photos are read, not typed.
	 */
	public List<Trip> matchingLr(long accountId, String lrNumber) {
		if (lrNumber == null || lrNumber.isBlank()) {
			return List.of();
		}
		String digits = lrNumber.replaceAll("\\D", "").replaceFirst("^0+", "");
		if (digits.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT * FROM trip WHERE account_id = ? AND status <> 'CANCELLED' AND lr_number LIKE ?
				ORDER BY created_at DESC
				""")
			.params(accountId, "%" + digits + "%")
			.query(Trip.class)
			.list()
			.stream()
			.filter((trip) -> Fuzzy.sameCode(trip.lrNumber(), lrNumber))
			.toList();
	}

	public long create(long accountId, TripForm form, String status) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO trip (account_id, lr_number, lr_date, party_id, vehicle_id, driver_id, origin, destination,
				    consignee, goods, freight_amount, advance_amount, detention_amount, other_charges, delivered_on,
				    status, notes)
				VALUES (:accountId, :lrNumber, :lrDate, :partyId, :vehicleId, :driverId, :origin, :destination,
				    :consignee, :goods, :freight, :advance, :detention, :other, :deliveredOn, :status, :notes)
				""")
			.paramSource(params(form).addValue("accountId", accountId).addValue("status", status))
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void update(long accountId, long id, TripForm form) {
		jdbc.sql("""
				UPDATE trip SET lr_number = :lrNumber, lr_date = :lrDate, party_id = :partyId, vehicle_id = :vehicleId,
				    driver_id = :driverId, origin = :origin, destination = :destination, consignee = :consignee,
				    goods = :goods, freight_amount = :freight, advance_amount = :advance, detention_amount = :detention,
				    other_charges = :other, delivered_on = :deliveredOn, notes = :notes
				WHERE account_id = :accountId AND id = :id
				""").paramSource(params(form).addValue("accountId", accountId).addValue("id", id)).update();
	}

	/** A proof of delivery arrived: the trip can now be billed. */
	public void markDelivered(long accountId, long id, LocalDate deliveredOn) {
		jdbc.sql("""
				UPDATE trip SET status = CASE WHEN status = 'IN_TRANSIT' THEN 'DELIVERED' ELSE status END,
				    delivered_on = COALESCE(delivered_on, ?)
				WHERE account_id = ? AND id = ?
				""").params(deliveredOn, accountId, id).update();
	}

	public void setStatus(long accountId, long id, String status) {
		jdbc.sql("UPDATE trip SET status = ? WHERE account_id = ? AND id = ?").params(status, accountId, id).update();
	}

	public void attachToInvoice(long accountId, List<Long> tripIds, long invoiceId) {
		jdbc.sql("UPDATE trip SET invoice_id = :invoiceId, status = 'INVOICED' WHERE account_id = :accountId AND id IN (:ids)")
			.param("invoiceId", invoiceId)
			.param("accountId", accountId)
			.param("ids", tripIds)
			.update();
	}

	public void markInvoicePaid(long accountId, long invoiceId) {
		jdbc.sql("UPDATE trip SET status = 'PAID' WHERE account_id = ? AND invoice_id = ?")
			.params(accountId, invoiceId)
			.update();
	}

	/** Delivered trips not yet billed: money the owner can invoice today. */
	public Unbilled unbilled(long accountId) {
		return jdbc.sql("""
				SELECT COUNT(*) AS trips,
				    COALESCE(SUM(COALESCE(freight_amount, 0) + detention_amount + other_charges - advance_amount), 0) AS amount
				FROM trip WHERE account_id = ? AND status = 'DELIVERED' AND invoice_id IS NULL
				""").param(accountId).query(Unbilled.class).single();
	}

	public long countInTransit(long accountId) {
		return jdbc.sql("SELECT COUNT(*) FROM trip WHERE account_id = ? AND status = 'IN_TRANSIT'")
			.param(accountId)
			.query(Long.class)
			.single();
	}

	/** All named parameters go in one source: JdbcClient ignores single params once a source is set. */
	private static MapSqlParameterSource params(TripForm form) {
		return new MapSqlParameterSource()
			.addValue("lrNumber", blank(form.getLrNumber()))
			.addValue("lrDate", form.getLrDate())
			.addValue("partyId", form.getPartyId())
			.addValue("vehicleId", form.getVehicleId())
			.addValue("driverId", form.getDriverId())
			.addValue("origin", blank(form.getOrigin()))
			.addValue("destination", blank(form.getDestination()))
			.addValue("consignee", blank(form.getConsignee()))
			.addValue("goods", blank(form.getGoods()))
			.addValue("freight", form.getFreightAmount())
			.addValue("advance", zero(form.getAdvanceAmount()))
			.addValue("detention", zero(form.getDetentionAmount()))
			.addValue("other", zero(form.getOtherCharges()))
			.addValue("deliveredOn", form.getDeliveredOn())
			.addValue("notes", blank(form.getNotes()));
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static BigDecimal zero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	public record TripRow(Long id, String lrNumber, LocalDate lrDate, Long partyId, String partyName, String vehicle,
			String origin, String destination, BigDecimal freightAmount, BigDecimal advanceAmount,
			BigDecimal detentionAmount, BigDecimal otherCharges, LocalDate deliveredOn, String status, Long invoiceId,
			long pods) {

		public String route() {
			if (origin == null && destination == null) {
				return "";
			}
			return (origin == null ? "?" : origin) + " → " + (destination == null ? "?" : destination);
		}

		public boolean billableNow() {
			return Trip.DELIVERED.equals(status) && invoiceId == null;
		}

	}

	public record Unbilled(long trips, BigDecimal amount) {
	}

}
