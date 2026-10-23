package com.liabletech.payoutcheck.statements;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.liabletech.payoutcheck.common.Money;
import com.liabletech.payoutcheck.ingest.OrderStatus;

/**
 * One row of a payout annexure. Deductions are stored as positive amounts; {@code otherAdjustment}
 * keeps its sign (positive is money to the restaurant). Rows without an order are adjustments
 * such as ad spend or recoveries.
 */
public record StatementLine(long id, long statementId, int lineNo, String orderRef, LocalDate orderDate, OrderStatus orderStatus,
		BigDecimal itemTotal, BigDecimal packaging, BigDecimal restaurantDiscount, BigDecimal commission, BigDecimal pgFee,
		BigDecimal otherFees, BigDecimal gstOnFees, BigDecimal tcs, BigDecimal tds, BigDecimal customerRefund,
		BigDecimal cancellationDeduction, BigDecimal penalty, BigDecimal otherAdjustment, BigDecimal netPayout,
		String description) {

	public boolean hasOrder() {
		return this.orderRef != null && !this.orderRef.isBlank();
	}

	public BigDecimal netOrderValue() {
		return Money.sum(this.itemTotal, this.packaging).subtract(Money.of(this.restaurantDiscount));
	}

	public BigDecimal totalDeductions() {
		return Money.sum(this.commission, this.pgFee, this.otherFees, this.gstOnFees, this.tcs, this.tds, this.customerRefund,
				this.cancellationDeduction, this.penalty);
	}

	/** What the row should pay out given its own components. */
	public BigDecimal computedNet() {
		return netOrderValue().subtract(totalDeductions()).add(Money.of(this.otherAdjustment));
	}

}
