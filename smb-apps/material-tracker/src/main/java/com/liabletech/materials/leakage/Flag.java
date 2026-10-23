package com.liabletech.materials.leakage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;

/** A gap between what arrived, what was agreed and what was billed, with its rupee impact. */
public record Flag(Long id, Long accountId, Long siteId, Long supplierId, Long billId, Long deliveryId, Long materialId,
		String kind, String message, BigDecimal expectedQty, BigDecimal actualQty, String unit, BigDecimal amount,
		String status, OffsetDateTime createdAt, OffsetDateTime resolvedAt, String resolutionNote) {

	/** Counted at the gate: less than the slip says. */
	public static final String SHORT_DELIVERY = "SHORT_DELIVERY";

	/** The bill charges for more than was received. */
	public static final String QTY_OVERBILLED = "QTY_OVERBILLED";

	/** The bill's rate is above the agreed rate. */
	public static final String RATE_ABOVE_AGREED = "RATE_ABOVE_AGREED";

	/** Quantity times rate, or the totals, do not add up in the supplier's favour. */
	public static final String ARITHMETIC = "ARITHMETIC";

	/** A challan already charged on another bill. */
	public static final String DUPLICATE_CHALLAN = "DUPLICATE_CHALLAN";

	/** The same bill number entered twice. */
	public static final String DUPLICATE_BILL = "DUPLICATE_BILL";

	/** The bill quotes a challan no delivery was recorded for. */
	public static final String CHALLAN_NOT_FOUND = "CHALLAN_NOT_FOUND";

	/** A line that could not be compared: unknown material or units that do not convert. */
	public static final String NEEDS_CHECK = "NEEDS_CHECK";

	public static final String OPEN = "OPEN";

	/** Sent to the supplier as part of a discrepancy note. */
	public static final String RAISED = "RAISED";

	/** Credit note received or the bill was corrected. */
	public static final String RECOVERED = "RECOVERED";

	public static final String DISMISSED = "DISMISSED";

	/** Settled by a later check, e.g. a gate shortage the supplier did not bill. */
	public static final String CLOSED = "CLOSED";

	public static final Set<String> AT_RISK = Set.of(OPEN, RAISED);

	public boolean atRisk() {
		return AT_RISK.contains(status);
	}

	public String title() {
		return switch (kind) {
			case SHORT_DELIVERY -> "Short delivery";
			case QTY_OVERBILLED -> "Billed more than received";
			case RATE_ABOVE_AGREED -> "Rate above agreed rate";
			case ARITHMETIC -> "Calculation error";
			case DUPLICATE_CHALLAN -> "Challan billed twice";
			case DUPLICATE_BILL -> "Duplicate bill";
			case CHALLAN_NOT_FOUND -> "Challan not received";
			default -> "Needs a check";
		};
	}

}
