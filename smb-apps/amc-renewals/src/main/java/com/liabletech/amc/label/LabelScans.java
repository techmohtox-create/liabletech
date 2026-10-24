package com.liabletech.amc.label;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class LabelScans {

	private final JdbcClient jdbc;

	public LabelScans(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public LabelScan get(long accountId, long id) {
		return jdbc.sql("SELECT * FROM label_scan WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(LabelScan.class)
			.optional()
			.orElseThrow(() -> new NotFoundException("Label " + id));
	}

	public long create(NewScan scan) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO label_scan (account_id, technician_id, file_id, caption, reading_status, reading_note, kind, brand,
				    model, serial_number, capacity, refrigerant, manufactured, customer_phone, customer_name, amc_sold, amount,
				    confidence, status, created_at)
				VALUES (:accountId, :technicianId, :fileId, :caption, :readingStatus, :readingNote, :kind, :brand,
				    :model, :serialNumber, :capacity, :refrigerant, :manufactured, :customerPhone, :customerName, :amcSold,
				    :amount, :confidence, 'NEEDS_REVIEW', :createdAt)
				""").paramSource(scan).update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void link(long id, long applianceId) {
		jdbc.sql("UPDATE label_scan SET status = 'LINKED', appliance_id = ? WHERE id = ?").params(applianceId, id).update();
	}

	public void reject(long accountId, long id) {
		jdbc.sql("UPDATE label_scan SET status = 'REJECTED' WHERE account_id = ? AND id = ?").params(accountId, id).update();
	}

	public List<ScanRow> list(long accountId, String status, int limit) {
		return jdbc.sql("""
				SELECT s.id, s.file_id, s.caption, s.reading_status, s.kind, s.brand, s.model, s.serial_number,
				    s.customer_phone, s.customer_name, s.amc_sold, s.status, s.appliance_id, s.created_at,
				    t.name AS technician_name, a.customer_id
				FROM label_scan s
				LEFT JOIN technician t ON t.id = s.technician_id
				LEFT JOIN appliance a ON a.id = s.appliance_id
				WHERE s.account_id = :accountId AND (CAST(:status AS VARCHAR(20)) IS NULL OR s.status = :status)
				ORDER BY s.created_at DESC, s.id DESC LIMIT :limit
				""").param("accountId", accountId).param("status", status).param("limit", limit).query(ScanRow.class).list();
	}

	public long countNeedingReview(long accountId) {
		return jdbc.sql("SELECT COUNT(*) FROM label_scan WHERE account_id = ? AND status = 'NEEDS_REVIEW'")
			.param(accountId)
			.query(Long.class)
			.single();
	}

	public record NewScan(long accountId, Long technicianId, long fileId, String caption, String readingStatus,
			String readingNote, String kind, String brand, String model, String serialNumber, String capacity,
			String refrigerant, String manufactured, String customerPhone, String customerName, boolean amcSold,
			BigDecimal amount, BigDecimal confidence, OffsetDateTime createdAt) {
	}

	public record ScanRow(Long id, Long fileId, String caption, String readingStatus, String kind, String brand,
			String model, String serialNumber, String customerPhone, String customerName, boolean amcSold, String status,
			Long applianceId, OffsetDateTime createdAt, String technicianName, Long customerId) {
	}

}
