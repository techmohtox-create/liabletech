package com.liabletech.payoutcheck.recon;

/** Everything the engine checks, with the wording used in dispute letters. */
public enum FindingType {

	MISSING_ORDER("Order not paid",
			"These delivered orders do not appear in the payout statement for the week they were placed in, or in any other statement we have."),
	STATUS_MISMATCH("Delivered order settled as cancelled",
			"Our records show these orders as delivered, but the statement settles them as cancelled or rejected."),
	VALUE_MISMATCH("Order value lower than billed",
			"The item total or packaging on the statement is lower than what the order was billed at."),
	DISCOUNT_OVERCHARGE("Restaurant discount higher than agreed",
			"The statement charges a larger restaurant-funded discount than the order actually carried."),
	COMMISSION_OVERCHARGE("Commission above contracted rate",
			"Commission was charged above the rate in our agreement."),
	PG_FEE_OVERCHARGE("Payment gateway fee above contracted rate",
			"The payment collection fee is above the agreed rate."),
	GST_ON_FEES_MISMATCH("GST on fees miscalculated", "GST charged on platform fees is more than 18% of those fees."),
	TDS_OVERDEDUCTED("TDS deducted above the legal rate",
			"TDS under section 194-O was deducted above the applicable rate on the order value."),
	TCS_OVERDEDUCTED("TCS deducted above the rate", "TCS was deducted above the applicable rate."),
	CUSTOMER_REFUND("Customer refund charged on a delivered order",
			"A customer complaint refund was recovered from us on orders that were delivered. Please share the complaint details and evidence; where the issue was not caused by the restaurant, reverse the deduction."),
	CANCELLATION_DEDUCTION("Cancellation deducted from payout",
			"A cancellation charge was deducted. Where the customer or the platform cancelled after we accepted or prepared the order, we expect compensation, not a deduction."),
	PENALTY("Penalty deducted",
			"A penalty was deducted. Please share the reason and the timestamps it is based on; delays caused by rider assignment are not the restaurant's fault."),
	DUPLICATE_DEDUCTION("Same charge deducted twice", "The same charge was deducted more than once for one order."),
	ARITHMETIC_ERROR("Net payout does not add up",
			"The net payout on these rows is lower than the order value minus the deductions listed on the same row."),
	UNEXPLAINED_ADJUSTMENT("Unexplained adjustment",
			"An adjustment was deducted without an order reference or an explanation. Please share what it is for."),
	AD_DEDUCTION("Ad or marketing deduction",
			"An advertising or marketing deduction was taken. Please share the campaign invoice so we can match it to what we booked."),
	CREDIT_SHORTFALL("Bank credit lower than statement",
			"The amount credited to our bank account is lower than the net payout on the statement.");

	private final String label;

	private final String disputeText;

	FindingType(String label, String disputeText) {
		this.label = label;
		this.disputeText = disputeText;
	}

	public String label() {
		return this.label;
	}

	public String disputeText() {
		return this.disputeText;
	}

}
