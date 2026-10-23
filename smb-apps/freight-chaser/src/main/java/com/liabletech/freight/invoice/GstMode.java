package com.liabletech.freight.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * How GST applies to a freight bill. Truck hire charged by an owner to a goods transport agency is
 * usually exempt; a GTA's own freight bill is usually under reverse charge (the recipient pays), or
 * under forward charge at a rate the GTA has opted for. The owner picks, ideally with their CA.
 */
public enum GstMode {

	/** No GST on this bill (exempt or not registered). */
	NONE("No GST"),
	/** GST payable by the recipient under reverse charge; nothing is added to the bill. */
	RCM("Reverse charge (recipient pays GST)"),
	/** GST charged on the bill at the chosen rate: CGST + SGST within a state, IGST across states. */
	FORWARD("GST charged on this bill");

	private final String label;

	GstMode(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	/**
	 * The totals for a bill. Tax is on the full freight; advances already received are deducted
	 * after tax; the amount payable is rounded to the rupee.
	 */
	public Totals compute(BigDecimal subtotal, BigDecimal rate, String supplierState, String recipientState,
			BigDecimal advance) {
		BigDecimal cgst = BigDecimal.ZERO;
		BigDecimal sgst = BigDecimal.ZERO;
		BigDecimal igst = BigDecimal.ZERO;
		if (this == FORWARD && rate != null && rate.signum() > 0) {
			BigDecimal tax = subtotal.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
			boolean sameState = supplierState != null && Objects.equals(supplierState, recipientState);
			if (sameState) {
				cgst = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
				sgst = tax.subtract(cgst);
			}
			else {
				igst = tax;
			}
		}
		BigDecimal exact = subtotal.add(cgst).add(sgst).add(igst).subtract(advance);
		BigDecimal total = exact.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
		return new Totals(subtotal, cgst, sgst, igst, total.subtract(exact), advance, total);
	}

	public record Totals(BigDecimal subtotal, BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal roundOff,
			BigDecimal advance, BigDecimal total) {
	}

}
