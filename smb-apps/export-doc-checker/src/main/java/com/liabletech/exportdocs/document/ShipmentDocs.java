package com.liabletech.exportdocs.document;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class ShipmentDocs {

	private final JdbcClient jdbc;

	public ShipmentDocs(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public ShipmentDoc get(long accountId, long id) {
		return jdbc.sql("SELECT * FROM shipment_doc WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(ShipmentDoc.class)
			.optional()
			.orElseThrow(() -> new NotFoundException("Document " + id));
	}

	public List<ShipmentDoc> forShipment(long shipmentId) {
		return jdbc.sql("SELECT * FROM shipment_doc WHERE shipment_id = ? ORDER BY id")
			.param(shipmentId)
			.query(ShipmentDoc.class)
			.list();
	}

	public Optional<ShipmentDoc> find(long shipmentId, DocType type) {
		return jdbc.sql("SELECT * FROM shipment_doc WHERE shipment_id = ? AND doc_type = ? ORDER BY id DESC LIMIT 1")
			.params(shipmentId, type.name())
			.query(ShipmentDoc.class)
			.optional();
	}

	/**
	 * Stores a document. A shipment has one document of each read type: a corrected bill of lading
	 * replaces the draft. "Other" documents accumulate.
	 */
	public long save(long accountId, long shipmentId, DocType type, Long fileId, String source, String readingStatus,
			String note, String json, BigDecimal confidence, OffsetDateTime at) {
		Optional<ShipmentDoc> existing = type == DocType.OTHER ? Optional.empty() : find(shipmentId, type);
		if (existing.isPresent()) {
			jdbc.sql("""
					UPDATE shipment_doc SET file_id = ?, source = ?, reading_status = ?, reading_note = ?, data_json = ?,
					    confidence = ?, updated_at = ?
					WHERE id = ?
					""")
				.params(fileId, source, readingStatus, cut(note), json, confidence, at, existing.get().id())
				.update();
			return existing.get().id();
		}
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO shipment_doc (account_id, shipment_id, doc_type, file_id, source, reading_status, reading_note,
				    data_json, confidence, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
			.params(accountId, shipmentId, type.name(), fileId, source, readingStatus, cut(note), json, confidence, at, at)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void updateData(long id, String json, String readingStatus, OffsetDateTime at) {
		jdbc.sql("UPDATE shipment_doc SET data_json = ?, reading_status = ?, updated_at = ? WHERE id = ?")
			.params(json, readingStatus, at, id)
			.update();
	}

	public void delete(long accountId, long id) {
		jdbc.sql("DELETE FROM shipment_doc WHERE account_id = ? AND id = ?").params(accountId, id).update();
	}

	private static String cut(String note) {
		return note == null || note.length() <= 500 ? note : note.substring(0, 497) + "...";
	}

}
