package com.liabletech.freight.pod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class Pods {

	private final JdbcClient jdbc;

	public Pods(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Pod get(long accountId, long id) {
		return jdbc.sql("SELECT * FROM pod WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Pod.class)
			.optional()
			.orElseThrow(() -> new NotFoundException("POD " + id));
	}

	long create(long accountId, long fileId, Long driverId, String source, String caption, OffsetDateTime at) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO pod (account_id, file_id, driver_id, source, caption, reading_status, review_status, received_at)
				VALUES (?, ?, ?, ?, ?, 'MANUAL', 'NEEDS_REVIEW', ?)
				""")
			.params(accountId, fileId, driverId, source, caption == null || caption.isBlank() ? null : caption.trim(), at)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	void saveReading(long id, String status, String note, Details details, BigDecimal confidence, String documentType) {
		jdbc.sql("""
				UPDATE pod SET reading_status = :status, reading_note = :note, document_type = :type,
				    lr_number = :lrNumber, lr_date = :lrDate, delivery_date = :deliveryDate, consignor = :consignor,
				    consignee = :consignee, origin = :origin, destination = :destination, vehicle_number = :vehicle,
				    packages = :packages, freight_amount = :freight, stamp_present = :stamp,
				    signature_present = :signature, remarks = :remarks, confidence = :confidence
				WHERE id = :id
				""")
			.param("id", id)
			.param("status", status)
			.param("note", note)
			.param("type", documentType)
			.param("lrNumber", details.lrNumber())
			.param("lrDate", details.lrDate())
			.param("deliveryDate", details.deliveryDate())
			.param("consignor", details.consignor())
			.param("consignee", details.consignee())
			.param("origin", details.origin())
			.param("destination", details.destination())
			.param("vehicle", details.vehicleNumber())
			.param("packages", details.packages())
			.param("freight", details.freightAmount())
			.param("stamp", details.stampPresent())
			.param("signature", details.signaturePresent())
			.param("remarks", details.remarks())
			.param("confidence", confidence)
			.update();
	}

	void link(long id, Long tripId, String reviewStatus, OffsetDateTime reviewedAt) {
		jdbc.sql("UPDATE pod SET trip_id = ?, review_status = ?, reviewed_at = ? WHERE id = ?")
			.params(tripId, reviewStatus, reviewedAt, id)
			.update();
	}

	public List<PodRow> list(long accountId, String reviewStatus, int limit) {
		return jdbc.sql("""
				SELECT pod.id, pod.file_id, pod.received_at, pod.source, pod.reading_status, pod.lr_number,
				    pod.delivery_date, pod.consignee, pod.stamp_present, pod.signature_present, pod.remarks,
				    pod.confidence, pod.review_status, pod.trip_id, t.lr_number AS trip_lr, d.name AS driver_name
				FROM pod
				LEFT JOIN trip t ON t.id = pod.trip_id
				LEFT JOIN driver d ON d.id = pod.driver_id
				WHERE pod.account_id = :accountId AND (CAST(:status AS VARCHAR(20)) IS NULL OR pod.review_status = :status)
				ORDER BY pod.received_at DESC, pod.id DESC LIMIT :limit
				""")
			.param("accountId", accountId)
			.param("status", reviewStatus)
			.param("limit", limit)
			.query(PodRow.class)
			.list();
	}

	public List<Pod> ofTrip(long accountId, long tripId) {
		return jdbc.sql("""
				SELECT * FROM pod WHERE account_id = ? AND trip_id = ? AND review_status <> 'REJECTED'
				ORDER BY received_at
				""").params(accountId, tripId).query(Pod.class).list();
	}

	public long countNeedingReview(long accountId) {
		return jdbc.sql("SELECT COUNT(*) FROM pod WHERE account_id = ? AND review_status = 'NEEDS_REVIEW'")
			.param(accountId)
			.query(Long.class)
			.single();
	}

	public List<Pod> recentFromDriver(long accountId, long driverId, int limit) {
		return jdbc.sql("SELECT * FROM pod WHERE account_id = ? AND driver_id = ? ORDER BY received_at DESC LIMIT ?")
			.params(accountId, driverId, limit)
			.query(Pod.class)
			.list();
	}

	/** Details read from (or typed in for) a POD. */
	public record Details(String lrNumber, LocalDate lrDate, LocalDate deliveryDate, String consignor,
			String consignee, String origin, String destination, String vehicleNumber, String packages,
			BigDecimal freightAmount, Boolean stampPresent, Boolean signaturePresent, String remarks) {
	}

	public record PodRow(Long id, Long fileId, OffsetDateTime receivedAt, String source, String readingStatus,
			String lrNumber, LocalDate deliveryDate, String consignee, Boolean stampPresent, Boolean signaturePresent,
			String remarks, BigDecimal confidence, String reviewStatus, Long tripId, String tripLr, String driverName) {
	}

}
