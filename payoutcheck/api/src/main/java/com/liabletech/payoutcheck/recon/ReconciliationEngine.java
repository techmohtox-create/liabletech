package com.liabletech.payoutcheck.recon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.liabletech.payoutcheck.common.Inr;
import com.liabletech.payoutcheck.common.Money;
import com.liabletech.payoutcheck.ingest.OrderStatus;
import com.liabletech.payoutcheck.orders.OrderRecord;
import com.liabletech.payoutcheck.outlet.PlatformTerms;
import com.liabletech.payoutcheck.statements.StatementLine;

/**
 * Checks one payout statement against the restaurant's orders and its contract terms. Pure: it
 * reads only what it is given, so every rule can be tested without a database.
 *
 * <p>
 * Amounts at risk are what the restaurant should get back if the dispute succeeds. Rules are
 * written not to count the same rupee twice: a commission overcharge includes the GST on the
 * excess, and the GST rule only checks GST against the fees actually charged.
 */
public final class ReconciliationEngine {

	private static final Pattern AD_WORDS = Pattern
		.compile("\\b(ads?|advert\\w*|marketing|promotion\\w*|campaign|cpc|sponsored|listing fee|visibility)\\b",
				Pattern.CASE_INSENSITIVE);

	private ReconciliationEngine() {
	}

	/**
	 * Everything the engine needs about one statement.
	 * @param ordersByRef the restaurant's records for the orders on the statement
	 * @param deliveredInPeriod delivered orders placed within the statement period
	 * @param settledElsewhere order references that appear in other statements
	 * @param priorLines rows for the same orders in statements for earlier periods
	 */
	public record Input(LocalDate periodStart, LocalDate periodEnd, BigDecimal creditedAmount, PlatformTerms terms,
			List<StatementLine> lines, Map<String, OrderRecord> ordersByRef, List<OrderRecord> deliveredInPeriod,
			Set<String> settledElsewhere, List<StatementLine> priorLines) {
	}

	public record Result(List<FindingDraft> findings, int matchedLines, int unmatchedLines, int ordersInPeriod) {
	}

	/** Fees the contract allows on an order of the given value. */
	public record ExpectedFees(BigDecimal netValue, BigDecimal commission, BigDecimal pgFee, BigDecimal gstOnFees,
			BigDecimal tcs, BigDecimal tds) {

		public BigDecimal netPayout() {
			return this.netValue.subtract(Money.sum(this.commission, this.pgFee, this.gstOnFees, this.tcs, this.tds));
		}

	}

	public static ExpectedFees expectedFees(PlatformTerms terms, BigDecimal itemTotal, BigDecimal packaging,
			BigDecimal restaurantDiscount) {
		BigDecimal netValue = Money.sum(itemTotal, packaging).subtract(Money.of(restaurantDiscount));
		BigDecimal commissionBase = terms.commissionOnPackaging() ? netValue : netValue.subtract(Money.of(packaging));
		BigDecimal commission = Money.percent(Money.positive(commissionBase), terms.commissionPct());
		BigDecimal pgFee = Money.percent(Money.positive(netValue), terms.pgFeePct());
		BigDecimal gst = Money.percent(commission.add(pgFee), terms.gstOnFeesPct());
		BigDecimal tcs = Money.percent(Money.positive(netValue), terms.tcsPct());
		BigDecimal tds = Money.percent(Money.positive(netValue), terms.tdsPct());
		return new ExpectedFees(netValue, commission, pgFee, gst, tcs, tds);
	}

	public static Result run(Input input) {
		PlatformTerms terms = input.terms();
		BigDecimal tolerance = Money.of(terms.tolerance());
		List<FindingDraft> findings = new ArrayList<>();
		Set<String> refsOnStatement = new HashSet<>();
		Map<String, List<StatementLine>> earlierLines = new HashMap<>();
		for (StatementLine prior : input.priorLines()) {
			earlierLines.computeIfAbsent(prior.orderRef(), (k) -> new ArrayList<>()).add(prior);
		}
		// An order can have several rows (the sale, then a refund or penalty row). Its value is
		// compared once, on the sum of its rows, at the first row that carries a sale.
		Map<String, BigDecimal> settledValue = new HashMap<>();
		Map<String, Integer> valueRow = new HashMap<>();
		for (StatementLine line : input.lines()) {
			if (line.hasOrder()) {
				settledValue.merge(line.orderRef(), Money.sum(line.itemTotal(), line.packaging()), BigDecimal::add);
				if (Money.of(line.itemTotal()).signum() > 0) {
					valueRow.putIfAbsent(line.orderRef(), line.lineNo());
				}
				else if (!valueRow.containsKey(line.orderRef())) {
					valueRow.put(line.orderRef(), -line.lineNo());
				}
			}
		}
		int matched = 0;
		int unmatched = 0;

		for (StatementLine line : input.lines()) {
			if (!line.hasOrder()) {
				checkAdjustment(line, findings);
				continue;
			}
			String ref = line.orderRef();
			refsOnStatement.add(ref);
			OrderRecord order = input.ordersByRef().get(ref);
			if (order != null) {
				matched++;
			}
			else {
				unmatched++;
			}
			List<StatementLine> earlier = earlierLines.computeIfAbsent(ref, (k) -> new ArrayList<>());
			Set<String> duplicated = checkDuplicates(line, earlier, findings);
			earlier.add(line);

			OrderStatus settledAs = line.orderStatus() != null ? line.orderStatus() : OrderStatus.DELIVERED;
			if (order != null && order.status() == OrderStatus.DELIVERED && settledAs != OrderStatus.DELIVERED
					&& settledAs != OrderStatus.OTHER) {
				ExpectedFees fees = expectedFees(terms, order.itemTotal(), order.packaging(), order.restaurantDiscount());
				BigDecimal atRisk = Money.positive(fees.netPayout().subtract(line.netPayout()));
				findings.add(draft(line, FindingType.STATUS_MISMATCH, Severity.HIGH, fees.netPayout(), line.netPayout(), atRisk,
						"Order %s was delivered (per your records) but settled as %s. Expected payout %s, received %s."
							.formatted(ref, settledAs.name().toLowerCase(), Inr.format(fees.netPayout()), Inr.format(line.netPayout())),
						""));
				checkArithmetic(line, tolerance, findings);
				continue;
			}
			OrderStatus status = order != null ? order.status() : settledAs;

			// Value is checked for delivered orders only, and not when this statement only carries a
			// refund or penalty row for a sale that was paid in another statement.
			boolean saleOnThisStatement = valueRow.get(ref) > 0;
			if (order != null && order.status() == OrderStatus.DELIVERED && Math.abs(valueRow.get(ref)) == line.lineNo()
					&& (saleOnThisStatement || !input.settledElsewhere().contains(ref))) {
				checkOrderValue(line, settledValue.get(ref), order, terms, tolerance, findings);
			}
			if (status == OrderStatus.DELIVERED && !duplicated.contains("commission")) {
				checkFees(line, terms, tolerance, findings);
			}
			checkTaxes(line, terms, tolerance, findings);
			checkDeductions(line, status, order, duplicated, findings);
			checkArithmetic(line, tolerance, findings);
		}

		checkMissingOrders(input, refsOnStatement, terms, findings);
		checkBankCredit(input, tolerance, findings);
		return new Result(findings, matched, unmatched, input.deliveredInPeriod().size());
	}

	private static void checkOrderValue(StatementLine line, BigDecimal settled, OrderRecord order, PlatformTerms terms,
			BigDecimal tolerance, List<FindingDraft> findings) {
		BigDecimal billed = Money.sum(order.itemTotal(), order.packaging());
		if (Money.exceeds(billed, settled, tolerance)) {
			// The shortfall is paid out net of the fees the contract charges on it.
			BigDecimal shortfall = billed.subtract(settled);
			BigDecimal atRisk = Money.positive(expectedFees(terms, shortfall, Money.ZERO, Money.ZERO).netPayout());
			findings.add(draft(line, FindingType.VALUE_MISMATCH, Severity.forAmount(atRisk), billed, settled, atRisk,
					"Order %s was billed at %s (items plus packaging) but settled on %s. After fees that is %s less paid out."
						.formatted(line.orderRef(), Inr.format(billed), Inr.format(settled), Inr.format(atRisk)),
					""));
		}
		BigDecimal discountGap = Money.of(line.restaurantDiscount()).subtract(Money.of(order.restaurantDiscount()));
		if (discountGap.compareTo(tolerance) > 0) {
			findings.add(draft(line, FindingType.DISCOUNT_OVERCHARGE, Severity.forAmount(discountGap),
					order.restaurantDiscount(), line.restaurantDiscount(), discountGap,
					"Order %s carried a restaurant discount of %s but the statement charges %s."
						.formatted(line.orderRef(), Inr.format(order.restaurantDiscount()), Inr.format(line.restaurantDiscount())),
					""));
		}
	}

	private static void checkFees(StatementLine line, PlatformTerms terms, BigDecimal tolerance, List<FindingDraft> findings) {
		ExpectedFees expected = expectedFees(terms, line.itemTotal(), line.packaging(), line.restaurantDiscount());
		BigDecimal gstFactor = BigDecimal.ONE.add(terms.gstOnFeesPct().movePointLeft(2));
		BigDecimal commission = Money.of(line.commission());
		if (Money.exceeds(commission, expected.commission(), tolerance)) {
			BigDecimal excess = commission.subtract(expected.commission());
			BigDecimal atRisk = Money.of(excess.multiply(gstFactor));
			BigDecimal chargedPct = expected.netValue().signum() > 0
					? commission.movePointRight(2).divide(expected.netValue(), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
			findings.add(draft(line, FindingType.COMMISSION_OVERCHARGE, Severity.forAmount(atRisk), expected.commission(),
					commission, atRisk,
					"Commission on order %s is %s (%s of %s). The agreed %s is %s. Excess including GST: %s.".formatted(
							line.orderRef(), Inr.format(commission), Inr.percent(chargedPct), Inr.format(expected.netValue()),
							Inr.percent(terms.commissionPct()), Inr.format(expected.commission()), Inr.format(atRisk)),
					""));
		}
		BigDecimal pgFee = Money.of(line.pgFee());
		if (Money.exceeds(pgFee, expected.pgFee(), tolerance)) {
			BigDecimal atRisk = Money.of(pgFee.subtract(expected.pgFee()).multiply(gstFactor));
			findings.add(draft(line, FindingType.PG_FEE_OVERCHARGE, Severity.forAmount(atRisk), expected.pgFee(), pgFee, atRisk,
					"Payment gateway fee on order %s is %s; %s of the order value is %s. Excess including GST: %s.".formatted(
							line.orderRef(), Inr.format(pgFee), Inr.percent(terms.pgFeePct()), Inr.format(expected.pgFee()),
							Inr.format(atRisk)),
					""));
		}
	}

	private static void checkTaxes(StatementLine line, PlatformTerms terms, BigDecimal tolerance, List<FindingDraft> findings) {
		BigDecimal fees = Money.sum(line.commission(), line.pgFee(), line.otherFees());
		BigDecimal expectedGst = Money.percent(fees, terms.gstOnFeesPct());
		BigDecimal gst = Money.of(line.gstOnFees());
		if (Money.exceeds(gst, expectedGst, tolerance)) {
			BigDecimal atRisk = gst.subtract(expectedGst);
			findings.add(draft(line, FindingType.GST_ON_FEES_MISMATCH, Severity.forAmount(atRisk), expectedGst, gst, atRisk,
					"GST on fees for order %s is %s; %s of the %s in fees is %s.".formatted(line.orderRef(), Inr.format(gst),
							Inr.percent(terms.gstOnFeesPct()), Inr.format(fees), Inr.format(expectedGst)),
					""));
		}
		BigDecimal value = Money.positive(line.netOrderValue());
		BigDecimal expectedTds = Money.percent(value, terms.tdsPct());
		BigDecimal tds = Money.of(line.tds());
		if (Money.exceeds(tds, expectedTds, tolerance)) {
			BigDecimal atRisk = tds.subtract(expectedTds);
			findings.add(draft(line, FindingType.TDS_OVERDEDUCTED, Severity.LOW, expectedTds, tds, atRisk,
					"TDS on order %s is %s; %s of %s is %s. Excess TDS can be claimed back in your income-tax return only if the platform reports it against your PAN, so check Form 26AS."
						.formatted(line.orderRef(), Inr.format(tds), Inr.percent(terms.tdsPct()), Inr.format(value),
								Inr.format(expectedTds)),
					""));
		}
		BigDecimal expectedTcs = Money.percent(value, terms.tcsPct());
		BigDecimal tcs = Money.of(line.tcs());
		if (Money.exceeds(tcs, expectedTcs, tolerance)) {
			BigDecimal atRisk = tcs.subtract(expectedTcs);
			findings.add(draft(line, FindingType.TCS_OVERDEDUCTED, Severity.LOW, expectedTcs, tcs, atRisk,
					"TCS on order %s is %s; your terms say %s (%s). Restaurant orders taxed under section 9(5) normally carry no GST TCS."
						.formatted(line.orderRef(), Inr.format(tcs), Inr.percent(terms.tcsPct()), Inr.format(expectedTcs)),
					""));
		}
	}

	private static void checkDeductions(StatementLine line, OrderStatus status, OrderRecord order, Set<String> duplicated,
			List<FindingDraft> findings) {
		BigDecimal refund = Money.of(line.customerRefund());
		if (refund.signum() > 0 && status == OrderStatus.DELIVERED && !duplicated.contains("refund")) {
			findings.add(draft(line, FindingType.CUSTOMER_REFUND, Severity.forAmount(refund).atLeast(Severity.MEDIUM), null, refund, refund,
					"%s was recovered from you for a customer complaint on order %s, which was delivered. Ask for the complaint and evidence; dispute it if the item was packed correctly."
						.formatted(Inr.format(refund), line.orderRef()),
					""));
		}
		BigDecimal cancellation = Money.of(line.cancellationDeduction());
		if (cancellation.signum() > 0) {
			boolean deliveredPerRecords = order != null && order.status() == OrderStatus.DELIVERED;
			Severity severity = deliveredPerRecords ? Severity.HIGH
					: status == OrderStatus.REJECTED ? Severity.LOW : Severity.MEDIUM;
			String why = deliveredPerRecords ? "your records show it as delivered"
					: status == OrderStatus.REJECTED ? "it was rejected; check whether the rejection was yours"
							: "if the customer or platform cancelled after you accepted, you are owed compensation instead";
			findings.add(draft(line, FindingType.CANCELLATION_DEDUCTION, severity, null, cancellation, cancellation,
					"%s cancellation deduction on order %s: %s.".formatted(Inr.format(cancellation), line.orderRef(), why), ""));
		}
		BigDecimal penalty = Money.of(line.penalty());
		if (penalty.signum() > 0 && !duplicated.contains("penalty")) {
			findings.add(draft(line, FindingType.PENALTY, Severity.MEDIUM, null, penalty, penalty,
					"%s penalty on order %s%s. Ask for the reason and timestamps; delays from rider assignment are disputable."
						.formatted(Inr.format(penalty), line.orderRef(),
								line.description() == null ? "" : " (\"" + line.description() + "\")"),
					""));
		}
	}

	/**
	 * A later row charging the same commission, refund or penalty for an order already charged.
	 * Returns what was duplicated, so the same rupees are not flagged again by other rules.
	 */
	private static Set<String> checkDuplicates(StatementLine line, List<StatementLine> earlier, List<FindingDraft> findings) {
		if (earlier.isEmpty()) {
			return Set.of();
		}
		BigDecimal duplicated = Money.ZERO;
		List<String> what = new ArrayList<>();
		for (StatementLine previous : earlier) {
			if (Money.of(line.commission()).signum() > 0 && Money.of(previous.commission()).signum() > 0
					&& !what.contains("commission")) {
				duplicated = duplicated.add(Money.sum(line.commission(), line.pgFee(), line.gstOnFees()));
				what.add("commission");
			}
			if (Money.of(line.customerRefund()).signum() > 0
					&& Money.of(line.customerRefund()).compareTo(Money.of(previous.customerRefund())) == 0
					&& !what.contains("refund")) {
				duplicated = duplicated.add(Money.of(line.customerRefund()));
				what.add("refund");
			}
			if (Money.of(line.penalty()).signum() > 0 && Money.of(line.penalty()).compareTo(Money.of(previous.penalty())) == 0
					&& !what.contains("penalty")) {
				duplicated = duplicated.add(Money.of(line.penalty()));
				what.add("penalty");
			}
		}
		if (duplicated.signum() > 0) {
			findings.add(draft(line, FindingType.DUPLICATE_DEDUCTION, Severity.HIGH, null, duplicated, duplicated,
					"Order %s is charged %s again on row %d after an earlier row already did: %s deducted twice."
						.formatted(line.orderRef(), String.join(", ", what), line.lineNo(), Inr.format(duplicated)),
					":" + line.lineNo()));
		}
		return new HashSet<>(what);
	}

	private static void checkArithmetic(StatementLine line, BigDecimal tolerance, List<FindingDraft> findings) {
		BigDecimal computed = line.computedNet();
		BigDecimal net = Money.of(line.netPayout());
		if (Money.exceeds(computed, net, tolerance)) {
			BigDecimal atRisk = computed.subtract(net);
			findings.add(draft(line, FindingType.ARITHMETIC_ERROR, Severity.forAmount(atRisk).atLeast(Severity.MEDIUM), computed, net, atRisk,
					"Row %d%s: order value minus listed deductions is %s, but the net payout is %s."
						.formatted(line.lineNo(), line.hasOrder() ? " (order " + line.orderRef() + ")" : "", Inr.format(computed),
								Inr.format(net)),
					":" + line.lineNo()));
		}
	}

	private static void checkAdjustment(StatementLine line, List<FindingDraft> findings) {
		BigDecimal adjustment = Money.of(line.otherAdjustment());
		BigDecimal amount = adjustment.signum() != 0 ? adjustment : Money.of(line.netPayout());
		if (amount.signum() >= 0) {
			return;
		}
		BigDecimal atRisk = amount.abs();
		String description = line.description() == null ? "" : line.description().trim();
		if (!description.isEmpty() && AD_WORDS.matcher(description).find()) {
			findings.add(draft(line, FindingType.AD_DEDUCTION, Severity.LOW, null, atRisk, atRisk,
					"%s deducted for \"%s\" (row %d). Match it against the ad invoice and the spend cap you set."
						.formatted(Inr.format(atRisk), description, line.lineNo()),
					":" + line.lineNo()));
			return;
		}
		findings.add(draft(line, FindingType.UNEXPLAINED_ADJUSTMENT, Severity.forAmount(atRisk).atMost(Severity.MEDIUM), null, atRisk, atRisk,
				"%s deducted on row %d with %s and no order reference.".formatted(Inr.format(atRisk), line.lineNo(),
						description.isEmpty() ? "no description" : "the note \"" + description + "\""),
				":" + line.lineNo()));
	}

	private static void checkMissingOrders(Input input, Set<String> refsOnStatement, PlatformTerms terms,
			List<FindingDraft> findings) {
		for (OrderRecord order : input.deliveredInPeriod()) {
			if (refsOnStatement.contains(order.orderRef()) || input.settledElsewhere().contains(order.orderRef())) {
				continue;
			}
			ExpectedFees fees = expectedFees(terms, order.itemTotal(), order.packaging(), order.restaurantDiscount());
			BigDecimal atRisk = Money.positive(fees.netPayout());
			findings.add(new FindingDraft(null, order.orderRef(), FindingType.MISSING_ORDER, Severity.HIGH, atRisk, Money.ZERO,
					atRisk,
					"Order %s (%s, %s) was delivered but is not on this statement or any other. Expected payout after fees: %s."
						.formatted(order.orderRef(), order.orderedAt().toLocalDate(), Inr.format(fees.netValue()),
								Inr.format(atRisk)),
					FindingType.MISSING_ORDER + ":" + order.orderRef()));
		}
	}

	private static void checkBankCredit(Input input, BigDecimal tolerance, List<FindingDraft> findings) {
		if (input.creditedAmount() == null) {
			return;
		}
		BigDecimal statementNet = input.lines().stream().map((l) -> Money.of(l.netPayout())).reduce(Money.ZERO, BigDecimal::add);
		BigDecimal credited = Money.of(input.creditedAmount());
		if (Money.exceeds(statementNet, credited, tolerance)) {
			BigDecimal atRisk = statementNet.subtract(credited);
			findings.add(new FindingDraft(null, null, FindingType.CREDIT_SHORTFALL, Severity.HIGH, statementNet, credited, atRisk,
					"The statement for %s to %s shows a net payout of %s, but %s reached the bank. Short by %s."
						.formatted(input.periodStart(), input.periodEnd(), Inr.format(statementNet), Inr.format(credited),
								Inr.format(atRisk)),
					FindingType.CREDIT_SHORTFALL.name()));
		}
	}

	private static FindingDraft draft(StatementLine line, FindingType type, Severity severity, BigDecimal expected,
			BigDecimal actual, BigDecimal atRisk, String explanation, String suffix) {
		String key = line.hasOrder() ? line.orderRef() : "row" + line.lineNo();
		return new FindingDraft(line.id() == 0 ? null : line.id(), line.orderRef(), type, severity, expected, actual,
				Money.of(atRisk), explanation, type + ":" + key + suffix);
	}

}
