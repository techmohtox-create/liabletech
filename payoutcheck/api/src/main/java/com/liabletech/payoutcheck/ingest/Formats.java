package com.liabletech.payoutcheck.ingest;

import java.util.List;

/**
 * The fields PayoutCheck reads and the header names they appear under. The first alias of each
 * field is the header used in the downloadable templates. The others cover the wording of common
 * POS exports and of Swiggy and Zomato payout annexures; annexure layouts change, so anything not
 * recognised can be renamed to the template header.
 */
public final class Formats {

	public static final List<ColumnSpec> ORDERS = List.of(
			ColumnSpec.required("orderRef", "Order ID", "order_id", "order id", "order no", "order number", "orderid",
					"zomato order id", "swiggy order id", "order #", "aggregator order id"),
			ColumnSpec.required("orderedAt", "Order date", "order_date", "order date", "ordered at", "order time",
					"order placed at", "created at", "date", "order date time", "placed on"),
			ColumnSpec.optional("status", "Status", "status", "order status", "order state", "state")
				.excluding("payment", "payout", "settlement"),
			ColumnSpec.optional("platform", "Platform", "platform", "channel", "aggregator", "source"),
			ColumnSpec.required("itemTotal", "Item total", "item_total", "item total", "subtotal", "sub total", "food total",
					"items total", "bill subtotal", "order value", "gross amount", "food value")
				.excluding("tax", "gst"),
			ColumnSpec.optional("packaging", "Packaging", "packaging", "packaging charges", "packaging charge", "packing charges",
					"container charges")
				.excluding("tax", "gst"),
			ColumnSpec.optional("restaurantDiscount", "Restaurant discount", "restaurant_discount", "restaurant discount",
					"merchant discount", "discount by restaurant", "restaurant funded discount", "discount")
				.excluding("platform", "swiggy", "zomato"));

	public static final List<ColumnSpec> STATEMENT = List.of(
			ColumnSpec.optional("orderRef", "Order ID", "order_id", "order id", "order no", "order number", "orderid",
					"zomato order id", "swiggy order id", "order #"),
			ColumnSpec.optional("orderDate", "Order date", "order_date", "order date", "order time", "date", "order placed at"),
			ColumnSpec.optional("orderStatus", "Order status", "order_status", "order status", "status", "order state")
				.excluding("payment", "payout", "settlement"),
			ColumnSpec.optional("itemTotal", "Item total", "item_total", "item total", "subtotal", "sub total", "food total",
					"bill subtotal", "order value", "items total")
				.excluding("tax", "gst"),
			ColumnSpec.optional("packaging", "Packaging", "packaging", "packaging charges", "packaging charge", "packing charges")
				.excluding("tax", "gst"),
			ColumnSpec.optional("restaurantDiscount", "Restaurant discount", "restaurant_discount", "restaurant discount",
					"restaurant discount share", "merchant discount", "discount share", "restaurant funded discount")
				.excluding("platform", "swiggy", "zomato"),
			ColumnSpec.optional("commission", "Commission", "commission", "platform commission", "commission charges",
					"service fee", "platform fee", "commissionable fee", "zomato commission", "swiggy commission")
				.excluding("tax", "gst", "base", "rate", "percent"),
			ColumnSpec.optional("pgFee", "PG fee", "pg_fee", "pg fee", "payment gateway fee", "payment collection charges",
					"payment mechanism fee", "pg charges", "payment gateway charges", "collection charges")
				.excluding("tax", "gst"),
			ColumnSpec.optional("otherFees", "Other fees", "other_fees", "other fees", "long distance fee", "call center charges",
					"other platform fees")
				.excluding("tax", "gst"),
			ColumnSpec.optional("gstOnFees", "GST on fees", "gst_on_fees", "gst on fees", "taxes on service fees",
					"taxes on service & payment collection charges", "taxes on zomato fees", "gst on commission",
					"gst on platform fees", "tax on fees", "taxes on fees", "service tax", "gst 18"),
			ColumnSpec.optional("tcs", "TCS", "tcs", "gst tcs", "tcs deducted", "tax collected at source"),
			ColumnSpec.optional("tds", "TDS", "tds", "tds 194o", "tds u/s 194o", "tds deducted", "income tax tds",
					"tax deducted at source"),
			ColumnSpec.optional("customerRefund", "Customer refund", "customer_refund", "customer refund", "customer complaints",
					"refund", "complaint deduction", "refund for complaints", "customer compensation", "refund deduction"),
			ColumnSpec.optional("cancellationDeduction", "Cancellation deduction", "cancellation_deduction",
					"cancellation deduction", "cancellation charges", "cancellation charge", "cancellation refund"),
			ColumnSpec.optional("penalty", "Penalty", "penalty", "penalties", "rejection penalty", "late penalty",
					"order rejection charges"),
			ColumnSpec.optional("otherAdjustment", "Other adjustment", "other_adjustment", "other adjustment", "adjustment",
					"adjustments", "misc adjustment", "other deductions", "recovery", "ads deduction")
				.excluding("tax", "gst"),
			ColumnSpec.required("netPayout", "Net payout", "net_payout", "net payout", "net payable", "payout amount",
					"net amount", "amount payable", "net settlement", "final payout", "net receivable"),
			ColumnSpec.optional("description", "Description", "description", "remarks", "narration", "particulars", "details",
					"reason"));

	private Formats() {
	}

}
