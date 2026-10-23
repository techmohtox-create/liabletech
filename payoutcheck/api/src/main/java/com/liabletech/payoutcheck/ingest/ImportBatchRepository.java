package com.liabletech.payoutcheck.ingest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.payoutcheck.common.Platform;
import com.liabletech.payoutcheck.ingest.ImportResult.RowError;

@Repository
public class ImportBatchRepository {

	private final JdbcClient jdbc;

	public ImportBatchRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public long insert(long orgId, long outletId, String kind, Platform platform, String fileName, int total, int imported,
			int rejected, List<RowError> errors, Long userId) {
		String errorText = errors.stream().map((e) -> "Row " + e.row() + ": " + e.message()).collect(Collectors.joining("\n"));
		KeyHolder keys = new GeneratedKeyHolder();
		this.jdbc.sql("""
				INSERT INTO import_batch (org_id, outlet_id, kind, platform, file_name, rows_total, rows_imported, rows_rejected,
				    errors, created_by, created_at)
				VALUES (:orgId, :outletId, :kind, :platform, :fileName, :total, :imported, :rejected, :errors, :userId, :now)
				""")
			.param("orgId", orgId)
			.param("outletId", outletId)
			.param("kind", kind)
			.param("platform", platform.name())
			.param("fileName", fileName)
			.param("total", total)
			.param("imported", imported)
			.param("rejected", rejected)
			.param("errors", errorText.isEmpty() ? null : errorText.substring(0, Math.min(errorText.length(), 8000)))
			.param("userId", userId)
			.param("now", OffsetDateTime.now(ZoneOffset.UTC))
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public List<ImportBatch> recent(long orgId, int limit) {
		return this.jdbc.sql("""
				SELECT id, outlet_id, kind, platform, file_name, rows_total, rows_imported, rows_rejected, errors, created_by, created_at
				FROM import_batch WHERE org_id = :orgId ORDER BY id DESC LIMIT :limit
				""")
			.param("orgId", orgId)
			.param("limit", limit)
			.query(ImportBatch.class)
			.list();
	}

}
