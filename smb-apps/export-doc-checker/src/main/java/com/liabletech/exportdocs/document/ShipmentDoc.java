package com.liabletech.exportdocs.document;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A document of a shipment and what was read from it. */
public record ShipmentDoc(Long id, Long accountId, Long shipmentId, String docType, Long fileId, String source,
		String readingStatus, String readingNote, String dataJson, BigDecimal confidence, OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	/** Read by AI from a photo or PDF. */
	public static final String READ = "READ";

	/** Parsed from the text of a SWIFT message. */
	public static final String PARSED = "PARSED";

	/** Not read automatically: the fields are typed in. */
	public static final String MANUAL = "MANUAL";

	/** Read, then corrected by hand. */
	public static final String EDITED = "EDITED";

	public DocType type() {
		return DocType.parse(docType);
	}

	public boolean needsTyping() {
		return MANUAL.equals(readingStatus);
	}

}
