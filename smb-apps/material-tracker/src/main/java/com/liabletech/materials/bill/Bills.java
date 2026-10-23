package com.liabletech.materials.bill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class Bills {

	private final JdbcClient jdbc;

	public Bills(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Bill get(long accountId, long id) {
		return jdbc.sql("SELECT * FROM supplier_bill WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Bill.class)
			.optional()
			.orElseThrow(() -> new NotFoundException("Bill " + id));
	}

	public long create(NewBill bill) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO supplier_bill (account_id, site_id, supplier_id, file_id, source, reading_status, reading_note,
				    supplier_name_read, bill_number, bill_date, challan_refs, taxable_amount, tax_amount, total_amount,
				    confidence, status, created_at)
				VALUES (:accountId, :siteId, :supplierId, :fileId, :source, :readingStatus, :readingNote,
				    :supplierNameRead, :billNumber, :billDate, :challanRefs, :taxableAmount, :taxAmount, :totalAmount,
				    :confidence, 'NEEDS_REVIEW', :createdAt)
				""").paramSource(bill).update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	void updateHeader(long accountId, long id, Long siteId, Long supplierId, String billNumber, LocalDate billDate,
			String challanRefs, BigDecimal taxable, BigDecimal tax, BigDecimal total) {
		jdbc.sql("""
				UPDATE supplier_bill SET site_id = ?, supplier_id = ?, bill_number = ?, bill_date = ?, challan_refs = ?,
				    taxable_amount = ?, tax_amount = ?, total_amount = ?
				WHERE account_id = ? AND id = ?
				""").params(siteId, supplierId, billNumber, billDate, challanRefs, taxable, tax, total, accountId, id).update();
	}

	public void addLine(long billId, int lineNo, Long materialId, String description, BigDecimal qty, String unit,
			BigDecimal rate, BigDecimal amount, String challanRef) {
		jdbc.sql("""
				INSERT INTO bill_line (bill_id, line_no, material_id, description, qty, unit, rate, amount, challan_ref)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
			.params(billId, lineNo, materialId, description.length() > 200 ? description.substring(0, 200) : description, qty,
					unit == null || unit.length() <= 10 ? unit : unit.substring(0, 10), rate, amount, challanRef)
			.update();
	}

	void deleteLines(long billId) {
		jdbc.sql("DELETE FROM bill_line WHERE bill_id = ?").param(billId).update();
	}

	public List<BillLine> lines(long billId) {
		return jdbc.sql("SELECT * FROM bill_line WHERE bill_id = ? ORDER BY line_no")
			.param(billId)
			.query(BillLine.class)
			.list();
	}

	public void setStatus(long accountId, long id, String status) {
		jdbc.sql("UPDATE supplier_bill SET status = ? WHERE account_id = ? AND id = ?").params(status, accountId, id).update();
	}

	public void markChecked(long id, OffsetDateTime at) {
		jdbc.sql("""
				UPDATE supplier_bill SET status = CASE WHEN status = 'NEEDS_REVIEW' THEN 'CHECKED' ELSE status END,
				    checked_at = ?
				WHERE id = ?
				""").params(at, id).update();
	}

	public void linkDeliveries(long billId, List<Long> deliveryIds) {
		jdbc.sql("DELETE FROM bill_delivery WHERE bill_id = ?").param(billId).update();
		for (Long deliveryId : deliveryIds) {
			jdbc.sql("INSERT INTO bill_delivery (bill_id, delivery_id) VALUES (?, ?)").params(billId, deliveryId).update();
		}
	}

	public List<Long> linkedDeliveryIds(long billId) {
		return jdbc.sql("SELECT delivery_id FROM bill_delivery WHERE bill_id = ? ORDER BY delivery_id")
			.param(billId)
			.query(Long.class)
			.list();
	}

	/**
	 * Earlier live bills that already charged for a delivery: the bill number per delivery id. The
	 * first bill to quote a challan owns it; later ones are the duplicates.
	 */
	public List<BilledElsewhere> billedElsewhere(long billId, List<Long> deliveryIds) {
		if (deliveryIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT bd.delivery_id, b.id AS bill_id, b.bill_number FROM bill_delivery bd
				JOIN supplier_bill b ON b.id = bd.bill_id
				WHERE bd.delivery_id IN (:ids) AND bd.bill_id < :billId AND b.status <> 'REJECTED'
				""").param("ids", deliveryIds).param("billId", billId).query(BilledElsewhere.class).list();
	}

	public boolean duplicateNumber(long accountId, long billId, Long supplierId, String billNumber) {
		if (supplierId == null || billNumber == null || billNumber.isBlank()) {
			return false;
		}
		return jdbc.sql("""
				SELECT COUNT(*) FROM supplier_bill WHERE account_id = ? AND supplier_id = ? AND id <> ?
				    AND UPPER(bill_number) = UPPER(?) AND status <> 'REJECTED'
				""").params(accountId, supplierId, billId, billNumber.trim()).query(Long.class).single() > 0;
	}

	public List<BillRow> list(long accountId, String status, Long siteId, int limit) {
		return jdbc.sql("""
				SELECT b.id, b.file_id, b.bill_number, b.bill_date, b.total_amount, b.status, b.reading_status, b.created_at,
				    s.name AS site_name, COALESCE(su.name, b.supplier_name_read) AS supplier_name,
				    (SELECT COALESCE(SUM(f.amount), 0) FROM leakage_flag f WHERE f.bill_id = b.id AND f.status IN ('OPEN', 'RAISED'))
				        AS at_risk,
				    (SELECT COUNT(*) FROM leakage_flag f WHERE f.bill_id = b.id AND f.status IN ('OPEN', 'RAISED')) AS open_flags
				FROM supplier_bill b
				LEFT JOIN site s ON s.id = b.site_id
				LEFT JOIN supplier su ON su.id = b.supplier_id
				WHERE b.account_id = :accountId
				    AND (CAST(:status AS VARCHAR(20)) IS NULL OR b.status = :status)
				    AND (CAST(:siteId AS BIGINT) IS NULL OR b.site_id = :siteId)
				ORDER BY b.created_at DESC, b.id DESC LIMIT :limit
				""")
			.param("accountId", accountId)
			.param("status", status)
			.param("siteId", siteId)
			.param("limit", limit)
			.query(BillRow.class)
			.list();
	}

	public long countNeedingReview(long accountId) {
		return jdbc.sql("SELECT COUNT(*) FROM supplier_bill WHERE account_id = ? AND status = 'NEEDS_REVIEW'")
			.param(accountId)
			.query(Long.class)
			.single();
	}

	public record NewBill(long accountId, Long siteId, Long supplierId, Long fileId, String source, String readingStatus,
			String readingNote, String supplierNameRead, String billNumber, LocalDate billDate, String challanRefs,
			BigDecimal taxableAmount, BigDecimal taxAmount, BigDecimal totalAmount, BigDecimal confidence,
			OffsetDateTime createdAt) {
	}

	public record BilledElsewhere(Long deliveryId, Long billId, String billNumber) {
	}

	public record BillRow(Long id, Long fileId, String billNumber, LocalDate billDate, BigDecimal totalAmount, String status,
			String readingStatus, OffsetDateTime createdAt, String siteName, String supplierName, BigDecimal atRisk,
			long openFlags) {
	}

}
