package com.liabletech.materials.leakage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class Flags {

	private final JdbcClient jdbc;

	public Flags(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void insert(long accountId, Long siteId, Long supplierId, Long billId, Long deliveryId, FlagDraft draft,
			OffsetDateTime at) {
		jdbc.sql("""
				INSERT INTO leakage_flag (account_id, site_id, supplier_id, bill_id, delivery_id, material_id, kind,
				    message, expected_qty, actual_qty, unit, amount, status, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)
				""")
			.params(accountId, siteId, supplierId, billId, draft.deliveryId() != null ? draft.deliveryId() : deliveryId,
					draft.materialId(), draft.kind(), truncate(draft.message()), draft.expectedQty(), draft.actualQty(),
					draft.unit(), draft.amount(), at)
			.update();
	}

	public Flag get(long accountId, long id) {
		return jdbc.sql("SELECT * FROM leakage_flag WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Flag.class)
			.optional()
			.orElseThrow(() -> new NotFoundException("Flag " + id));
	}

	public List<Flag> forBill(long accountId, long billId) {
		return jdbc.sql("SELECT * FROM leakage_flag WHERE account_id = ? AND bill_id = ? ORDER BY amount DESC, id")
			.params(accountId, billId)
			.query(Flag.class)
			.list();
	}

	public List<Flag> forDelivery(long accountId, long deliveryId) {
		return jdbc.sql("SELECT * FROM leakage_flag WHERE account_id = ? AND delivery_id = ? ORDER BY id")
			.params(accountId, deliveryId)
			.query(Flag.class)
			.list();
	}

	/** Gate shortages still open on the given deliveries. */
	public List<Flag> openShortages(long accountId, List<Long> deliveryIds) {
		if (deliveryIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT * FROM leakage_flag WHERE account_id = :accountId AND kind = 'SHORT_DELIVERY' AND status = 'OPEN'
				    AND delivery_id IN (:ids)
				""").param("accountId", accountId).param("ids", deliveryIds).query(Flag.class).list();
	}

	/** Re-checking a bill replaces its open findings; ones already raised or settled stay. */
	public void deleteOpenForBill(long accountId, long billId) {
		jdbc.sql("DELETE FROM leakage_flag WHERE account_id = ? AND bill_id = ? AND status = 'OPEN'")
			.params(accountId, billId)
			.update();
	}

	public void deleteOpenShortages(long accountId, long deliveryId) {
		jdbc.sql("DELETE FROM leakage_flag WHERE account_id = ? AND delivery_id = ? AND kind = 'SHORT_DELIVERY' AND status = 'OPEN'")
			.params(accountId, deliveryId)
			.update();
	}

	public void setStatus(long accountId, long id, String status, String note, OffsetDateTime at) {
		jdbc.sql("UPDATE leakage_flag SET status = ?, resolution_note = ?, resolved_at = ? WHERE account_id = ? AND id = ?")
			.params(status, note, Flag.AT_RISK.contains(status) ? null : at, accountId, id)
			.update();
	}

	public List<FlagRow> list(long accountId, String status, Long siteId, Long supplierId, int limit) {
		return jdbc.sql("""
				SELECT f.*, s.name AS site_name, su.name AS supplier_name, m.name AS material_name,
				    b.bill_number AS bill_number
				FROM leakage_flag f
				LEFT JOIN site s ON s.id = f.site_id
				LEFT JOIN supplier su ON su.id = f.supplier_id
				LEFT JOIN material m ON m.id = f.material_id
				LEFT JOIN supplier_bill b ON b.id = f.bill_id
				WHERE f.account_id = :accountId
				    AND (:status = 'ALL' OR (:status = 'AT_RISK' AND f.status IN ('OPEN', 'RAISED')) OR f.status = :status)
				    AND (CAST(:siteId AS BIGINT) IS NULL OR f.site_id = :siteId)
				    AND (CAST(:supplierId AS BIGINT) IS NULL OR f.supplier_id = :supplierId)
				ORDER BY f.created_at DESC, f.id DESC LIMIT :limit
				""")
			.param("accountId", accountId)
			.param("status", status)
			.param("siteId", siteId)
			.param("supplierId", supplierId)
			.param("limit", limit)
			.query(FlagRow.class)
			.list();
	}

	public Totals totals(long accountId, OffsetDateTime since) {
		return jdbc.sql("""
				SELECT COALESCE(SUM(CASE WHEN status IN ('OPEN', 'RAISED') THEN amount END), 0) AS at_risk,
				    COALESCE(SUM(CASE WHEN status = 'RECOVERED' THEN amount END), 0) AS recovered,
				    COALESCE(SUM(CASE WHEN created_at >= ? AND status IN ('OPEN', 'RAISED', 'RECOVERED') THEN amount END), 0)
				        AS caught_this_month,
				    COUNT(CASE WHEN status = 'OPEN' THEN 1 END) AS open_count
				FROM leakage_flag WHERE account_id = ?
				""").params(since, accountId).query(Totals.class).single();
	}

	/** At-risk and recovered amounts per site or per supplier, for the dashboard. */
	public List<Breakdown> bySite(long accountId) {
		return jdbc.sql("""
				SELECT s.id AS id, s.name AS name,
				    COALESCE(SUM(CASE WHEN f.status IN ('OPEN', 'RAISED') THEN f.amount END), 0) AS at_risk,
				    COALESCE(SUM(CASE WHEN f.status = 'RECOVERED' THEN f.amount END), 0) AS recovered,
				    COUNT(f.id) AS flags
				FROM site s LEFT JOIN leakage_flag f ON f.site_id = s.id
				WHERE s.account_id = ?
				GROUP BY s.id, s.name ORDER BY at_risk DESC, s.name
				""").param(accountId).query(Breakdown.class).list();
	}

	public List<Breakdown> bySupplier(long accountId) {
		return jdbc.sql("""
				SELECT su.id AS id, su.name AS name,
				    COALESCE(SUM(CASE WHEN f.status IN ('OPEN', 'RAISED') THEN f.amount END), 0) AS at_risk,
				    COALESCE(SUM(CASE WHEN f.status = 'RECOVERED' THEN f.amount END), 0) AS recovered,
				    COUNT(f.id) AS flags
				FROM supplier su LEFT JOIN leakage_flag f ON f.supplier_id = su.id
				WHERE su.account_id = ?
				GROUP BY su.id, su.name ORDER BY at_risk DESC, su.name
				""").param(accountId).query(Breakdown.class).list();
	}

	private static String truncate(String message) {
		return message.length() <= 500 ? message : message.substring(0, 499) + "…";
	}

	public record FlagRow(Long id, Long siteId, String siteName, Long supplierId, String supplierName, Long billId,
			String billNumber, Long deliveryId, Long materialId, String materialName, String kind, String message,
			BigDecimal amount, String status, OffsetDateTime createdAt, String resolutionNote) {

		public String title() {
			return new Flag(id, null, siteId, supplierId, billId, deliveryId, materialId, kind, message, null, null, null,
					amount, status, createdAt, null, resolutionNote).title();
		}

	}

	public record Totals(BigDecimal atRisk, BigDecimal recovered, BigDecimal caughtThisMonth, long openCount) {
	}

	public record Breakdown(Long id, String name, BigDecimal atRisk, BigDecimal recovered, long flags) {
	}

}
