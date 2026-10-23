package com.liabletech.materials.bill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/** A supplier's bill for materials, as read from a photo or PDF and corrected by the office. */
public record Bill(Long id, Long accountId, Long siteId, Long supplierId, Long fileId, String source,
		String readingStatus, String readingNote, String supplierNameRead, String billNumber, LocalDate billDate,
		String challanRefs, BigDecimal taxableAmount, BigDecimal taxAmount, BigDecimal totalAmount,
		BigDecimal confidence, String status, OffsetDateTime checkedAt, OffsetDateTime createdAt) {

	/** Read, waiting for the office to correct and check it. */
	public static final String NEEDS_REVIEW = "NEEDS_REVIEW";

	/** Checked against deliveries; see its flags. */
	public static final String CHECKED = "CHECKED";

	/** Fine to pay. */
	public static final String APPROVED = "APPROVED";

	/** Discrepancy note sent to the supplier. */
	public static final String DISPUTED = "DISPUTED";

	public static final String PAID = "PAID";

	/** Not a bill, or entered twice by mistake. */
	public static final String REJECTED = "REJECTED";

	public List<String> challanRefList() {
		if (challanRefs == null || challanRefs.isBlank()) {
			return List.of();
		}
		return Arrays.stream(challanRefs.split(",")).map(String::trim).filter((r) -> !r.isEmpty()).toList();
	}

}
