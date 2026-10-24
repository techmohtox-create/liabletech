package com.liabletech.exportdocs.check;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.liabletech.exportdocs.document.DocType;

/** A stored finding and what the exporter decided about it. */
public record Finding(Long id, Long accountId, Long shipmentId, String severity, String code, String fingerprint,
		String title, String detail, String expected, String found, String documents, String ruleRef, String fixer,
		String fix, String status, String note, OffsetDateTime createdAt, OffsetDateTime resolvedAt) {

	public static final String OPEN = "OPEN";

	/** The exporter accepts the risk: e.g. the buyer has agreed to waive it. */
	public static final String ACCEPTED = "ACCEPTED";

	/** No longer found by the latest check. */
	public static final String FIXED = "FIXED";

	public Severity level() {
		return Severity.valueOf(severity);
	}

	public Fixer who() {
		return Fixer.valueOf(fixer);
	}

	public boolean open() {
		return OPEN.equals(status);
	}

	public List<DocType> docTypes() {
		if (documents == null || documents.isBlank()) {
			return List.of();
		}
		return Arrays.stream(documents.split(",")).map(DocType::parse).filter(Objects::nonNull).toList();
	}

}
