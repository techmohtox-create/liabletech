package com.liabletech.amc.request;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class ServiceRequests {

	private final JdbcClient jdbc;

	public ServiceRequests(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public ServiceRequest get(long accountId, long id) {
		return jdbc.sql("SELECT * FROM service_request WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(ServiceRequest.class)
			.optional()
			.orElseThrow(() -> new NotFoundException("Request " + id));
	}

	public long create(long accountId, long customerId, Long contractId, Long applianceId, String kind, String source,
			Long planId, LocalDate preferredOn, String slot, String note, OffsetDateTime at) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO service_request (account_id, customer_id, contract_id, appliance_id, kind, source, status, plan_id,
				    preferred_on, preferred_slot, note, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, 'NEW', ?, ?, ?, ?, ?, ?)
				""")
			.params(accountId, customerId, contractId, applianceId, kind, source, planId, preferredOn, slot,
					note == null || note.isBlank() ? null : note.trim(), at, at)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	/** An open request of this kind for the contract, so a second tap on the link does not add another. */
	public Optional<ServiceRequest> openFor(long contractId, String kind) {
		return jdbc.sql("""
				SELECT * FROM service_request WHERE contract_id = ? AND kind = ? AND status IN ('NEW', 'SCHEDULED')
				ORDER BY id DESC LIMIT 1
				""").params(contractId, kind).query(ServiceRequest.class).optional();
	}

	public void schedule(long accountId, long id, LocalDate on, Long technicianId, OffsetDateTime at) {
		jdbc.sql("""
				UPDATE service_request SET status = 'SCHEDULED', scheduled_on = ?, technician_id = ?, updated_at = ?
				WHERE account_id = ? AND id = ?
				""").params(on, technicianId, at, accountId, id).update();
	}

	public void finish(long accountId, long id, String status, LocalDate doneOn, OffsetDateTime at) {
		jdbc.sql("UPDATE service_request SET status = ?, done_on = ?, updated_at = ? WHERE account_id = ? AND id = ?")
			.params(status, doneOn, at, accountId, id)
			.update();
	}

	/** Service visits done under a contract, for its visit count. */
	public long visitsDone(long contractId) {
		return jdbc.sql("""
				SELECT COUNT(*) FROM service_request WHERE contract_id = ? AND kind = 'SERVICE' AND status = 'DONE'
				""").param(contractId).query(Long.class).single();
	}

	public List<RequestRow> list(long accountId, List<String> statuses, int limit) {
		return rows(accountId, null, statuses, limit);
	}

	public List<RequestRow> forCustomer(long accountId, long customerId) {
		return rows(accountId, customerId, List.of("NEW", "SCHEDULED", "DONE", "DISMISSED"), 200);
	}

	private List<RequestRow> rows(long accountId, Long customerId, List<String> statuses, int limit) {
		return jdbc.sql("""
				SELECT q.id, q.kind, q.source, q.status, q.preferred_on, q.preferred_slot, q.scheduled_on, q.note, q.done_on,
				    q.created_at, q.contract_id, q.customer_id, q.appliance_id, q.plan_id, q.technician_id,
				    c.name AS customer_name, c.phone, c.area, a.kind AS appliance_kind, a.brand, a.capacity,
				    p.name AS plan_name, p.price AS plan_price, t.name AS technician_name, k.ends_on, k.price AS contract_price,
				    k.plan_name AS contract_plan, k.status AS contract_status
				FROM service_request q
				JOIN customer c ON c.id = q.customer_id
				LEFT JOIN appliance a ON a.id = q.appliance_id
				LEFT JOIN amc_plan p ON p.id = q.plan_id
				LEFT JOIN technician t ON t.id = q.technician_id
				LEFT JOIN contract k ON k.id = q.contract_id
				WHERE q.account_id = :accountId AND q.status IN (:statuses)
				    AND (CAST(:customerId AS BIGINT) IS NULL OR q.customer_id = :customerId)
				ORDER BY CASE q.status WHEN 'NEW' THEN 0 WHEN 'SCHEDULED' THEN 1 ELSE 2 END, q.scheduled_on NULLS LAST,
				    q.created_at DESC
				LIMIT :limit
				""")
			.param("accountId", accountId)
			.param("customerId", customerId)
			.param("statuses", statuses)
			.param("limit", limit)
			.query(RequestRow.class)
			.list();
	}

	public long countNew(long accountId) {
		return jdbc.sql("SELECT COUNT(*) FROM service_request WHERE account_id = ? AND status = 'NEW'")
			.param(accountId)
			.query(Long.class)
			.single();
	}

	public record RequestRow(Long id, String kind, String source, String status, LocalDate preferredOn,
			String preferredSlot, LocalDate scheduledOn, String note, LocalDate doneOn, OffsetDateTime createdAt,
			Long contractId, Long customerId, Long applianceId, Long planId, Long technicianId, String customerName,
			String phone, String area, String applianceKind, String brand, String capacity, String planName,
			java.math.BigDecimal planPrice, String technicianName, LocalDate endsOn, java.math.BigDecimal contractPrice,
			String contractPlan, String contractStatus) {

		/** A renewal that can still be confirmed: its contract has not been renewed or cancelled meanwhile. */
		public boolean renewable() {
			return "RENEWAL".equals(kind) && "ACTIVE".equals(contractStatus);
		}

		/** The appliance's plan family (AC, RO...), or null when the request names no appliance. */
		public String family() {
			var parsed = com.liabletech.amc.customer.ApplianceKind.parse(applianceKind);
			return parsed == null ? null : parsed.family();
		}

		public String appliance() {
			var kind = com.liabletech.amc.customer.ApplianceKind.parse(applianceKind);
			if (kind == null) {
				return null;
			}
			return (brand == null ? "" : brand + " ") + (capacity == null ? "" : capacity + " ") + kind.label();
		}

	}

}
