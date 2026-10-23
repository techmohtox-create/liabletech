package com.liabletech.payoutcheck.ingest;

import java.util.List;
import java.util.Map;

import com.liabletech.payoutcheck.recon.ReconciliationService.CheckSummary;

public record ImportResult(long importId, String kind, String fileName, int rowsRead, int imported, int newRecords,
		int skipped, int rejected, List<RowError> errors, Map<String, String> columns, Long statementId, CheckSummary check,
		int recheckedStatements) {

	public record RowError(int row, String message) {
	}

}
