package com.liabletech.payoutcheck.ingest;

import java.time.OffsetDateTime;

import com.liabletech.payoutcheck.common.Platform;

public record ImportBatch(long id, long outletId, String kind, Platform platform, String fileName, int rowsTotal,
		int rowsImported, int rowsRejected, String errors, Long createdBy, OffsetDateTime createdAt) {
}
