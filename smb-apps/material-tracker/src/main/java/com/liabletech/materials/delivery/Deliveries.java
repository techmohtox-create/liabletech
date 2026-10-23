package com.liabletech.materials.delivery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class Deliveries {

	private final JdbcClient jdbc;

	public Deliveries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Delivery get(long accountId, long id) {
		return find(accountId, id).orElseThrow(() -> new NotFoundException("Delivery " + id));
	}

	public Optional<Delivery> find(long accountId, long id) {
		return jdbc.sql("SELECT * FROM delivery WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Delivery.class)
			.optional();
	}

	long create(NewDelivery d) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO delivery (account_id, site_id, supplier_id, member_id, file_id, source, caption,
				    reading_status, reading_note, supplier_name_read, challan_number, challan_date, vehicle_number,
				    confidence, status, received_at)
				VALUES (:accountId, :siteId, :supplierId, :memberId, :fileId, :source, :caption, :readingStatus,
				    :readingNote, :supplierNameRead, :challanNumber, :challanDate, :vehicleNumber, :confidence, :status,
				    :receivedAt)
				""").paramSource(d).update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	void addLine(long deliveryId, int lineNo, Long materialId, String description, BigDecimal slipQty, String unit,
			BigDecimal receivedQty) {
		jdbc.sql("""
				INSERT INTO delivery_line (delivery_id, line_no, material_id, description, slip_qty, unit, received_qty)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""").params(deliveryId, lineNo, materialId, truncate(description, 200), slipQty, truncate(unit, 10),
				receivedQty).update();
	}

	void deleteLines(long deliveryId) {
		jdbc.sql("DELETE FROM delivery_line WHERE delivery_id = ?").param(deliveryId).update();
	}

	void setReceived(long lineId, BigDecimal receivedQty) {
		jdbc.sql("UPDATE delivery_line SET received_qty = ? WHERE id = ?").params(receivedQty, lineId).update();
	}

	public List<DeliveryLine> lines(long deliveryId) {
		return jdbc.sql("SELECT * FROM delivery_line WHERE delivery_id = ? ORDER BY line_no")
			.param(deliveryId)
			.query(DeliveryLine.class)
			.list();
	}

	void updateHeader(long accountId, long id, long siteId, Long supplierId, String challanNumber, LocalDate challanDate,
			String vehicleNumber, String notes) {
		jdbc.sql("""
				UPDATE delivery SET site_id = ?, supplier_id = ?, challan_number = ?, challan_date = ?,
				    vehicle_number = ?, notes = ?
				WHERE account_id = ? AND id = ?
				""").params(siteId, supplierId, challanNumber, challanDate, vehicleNumber, notes, accountId, id).update();
	}

	void setStatus(long id, String status, OffsetDateTime confirmedAt) {
		jdbc.sql("UPDATE delivery SET status = ?, confirmed_at = COALESCE(?, confirmed_at) WHERE id = ?")
			.params(status, confirmedAt, id)
			.update();
	}

	/** The supervisor's latest slip still waiting for their count, which a reply like "OK" confirms. */
	public Optional<Delivery> latestAwaitingCount(long accountId, long memberId) {
		return jdbc.sql("""
				SELECT * FROM delivery WHERE account_id = ? AND member_id = ? AND status = 'AWAITING_COUNT'
				ORDER BY received_at DESC, id DESC LIMIT 1
				""").params(accountId, memberId).query(Delivery.class).optional();
	}

	public List<DeliveryRow> list(long accountId, Long siteId, String status, int limit) {
		return jdbc.sql("""
				SELECT d.id, d.file_id, d.received_at, d.challan_number, d.challan_date, d.status, d.reading_status,
				    d.site_id, s.name AS site_name, d.supplier_id, COALESCE(su.name, d.supplier_name_read) AS supplier_name,
				    m.name AS member_name,
				    (SELECT COUNT(*) FROM delivery_line l WHERE l.delivery_id = d.id) AS line_count,
				    (SELECT COUNT(*) FROM delivery_line l WHERE l.delivery_id = d.id AND l.received_qty < l.slip_qty) AS short_lines,
				    (SELECT MIN(b.bill_id) FROM bill_delivery b WHERE b.delivery_id = d.id) AS bill_id
				FROM delivery d
				JOIN site s ON s.id = d.site_id
				LEFT JOIN supplier su ON su.id = d.supplier_id
				LEFT JOIN site_member m ON m.id = d.member_id
				WHERE d.account_id = :accountId
				    AND (CAST(:siteId AS BIGINT) IS NULL OR d.site_id = :siteId)
				    AND (CAST(:status AS VARCHAR(20)) IS NULL OR d.status = :status)
				ORDER BY d.received_at DESC, d.id DESC LIMIT :limit
				""")
			.param("accountId", accountId)
			.param("siteId", siteId)
			.param("status", status)
			.param("limit", limit)
			.query(DeliveryRow.class)
			.list();
	}

	/** Confirmed deliveries from a supplier to a site in a date window that no other bill has used. */
	public List<Delivery> unbilledFor(long accountId, long supplierId, Long siteId, LocalDate from, LocalDate to,
			long exceptBillId) {
		return jdbc.sql("""
				SELECT * FROM delivery d
				WHERE d.account_id = :accountId AND d.supplier_id = :supplierId AND d.status = 'CONFIRMED'
				    AND (CAST(:siteId AS BIGINT) IS NULL OR d.site_id = :siteId)
				    AND COALESCE(d.challan_date, CAST(d.received_at AS DATE)) BETWEEN :from AND :to
				    AND NOT EXISTS (SELECT 1 FROM bill_delivery bd JOIN supplier_bill b ON b.id = bd.bill_id
				        WHERE bd.delivery_id = d.id AND bd.bill_id <> :billId AND b.status <> 'REJECTED')
				ORDER BY d.received_at
				""")
			.param("accountId", accountId)
			.param("supplierId", supplierId)
			.param("siteId", siteId)
			.param("from", from)
			.param("to", to)
			.param("billId", exceptBillId)
			.query(Delivery.class)
			.list();
	}

	/** Deliveries from a supplier whose challan number matches, whatever their date. */
	public List<Delivery> byChallan(long accountId, long supplierId, String challanNumber) {
		String digits = challanNumber == null ? "" : challanNumber.replaceAll("\\D", "").replaceFirst("^0+", "");
		if (digits.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT * FROM delivery WHERE account_id = ? AND supplier_id = ? AND status <> 'REJECTED'
				    AND challan_number LIKE ?
				""").params(accountId, supplierId, "%" + digits + "%").query(Delivery.class).list();
	}

	public long countAwaiting(long accountId) {
		return jdbc.sql("SELECT COUNT(*) FROM delivery WHERE account_id = ? AND status IN ('AWAITING_COUNT', 'NEEDS_REVIEW')")
			.param(accountId)
			.query(Long.class)
			.single();
	}

	static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	public record NewDelivery(long accountId, long siteId, Long supplierId, Long memberId, Long fileId, String source,
			String caption, String readingStatus, String readingNote, String supplierNameRead, String challanNumber,
			LocalDate challanDate, String vehicleNumber, BigDecimal confidence, String status,
			OffsetDateTime receivedAt) {
	}

	public record DeliveryRow(Long id, Long fileId, OffsetDateTime receivedAt, String challanNumber, LocalDate challanDate,
			String status, String readingStatus, Long siteId, String siteName, Long supplierId, String supplierName,
			String memberName, long lineCount, long shortLines, Long billId) {
	}

}
