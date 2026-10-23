package com.liabletech.payoutcheck.recon;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.liabletech.payoutcheck.common.Money;
import com.liabletech.payoutcheck.common.Platform;
import com.liabletech.payoutcheck.ingest.OrderStatus;
import com.liabletech.payoutcheck.orders.OrderRecord;
import com.liabletech.payoutcheck.outlet.PlatformTerms;
import com.liabletech.payoutcheck.recon.ReconciliationEngine.ExpectedFees;
import com.liabletech.payoutcheck.statements.StatementLine;

class ReconciliationEngineTests {

	private static final LocalDate START = LocalDate.of(2026, 10, 12);

	private static final LocalDate END = LocalDate.of(2026, 10, 18);

	/** Swiggy defaults: 18% commission, 2% PG, 18% GST on fees, 0.1% TDS, ₹1 tolerance. */
	private final PlatformTerms terms = PlatformTerms.defaults(1, Platform.SWIGGY);

	private final Map<String, OrderRecord> orders = new HashMap<>();

	private final List<StatementLine> lines = new ArrayList<>();

	private BigDecimal credited;

	@Test
	void expectedFeesFollowTheContract() {
		ExpectedFees fees = ReconciliationEngine.expectedFees(this.terms, bd("450"), bd("20"), bd("40"));
		assertThat(fees.netValue()).isEqualByComparingTo("430.00");
		assertThat(fees.commission()).isEqualByComparingTo("77.40");
		assertThat(fees.pgFee()).isEqualByComparingTo("8.60");
		assertThat(fees.gstOnFees()).isEqualByComparingTo("15.48");
		assertThat(fees.tds()).isEqualByComparingTo("0.43");
		assertThat(fees.netPayout()).isEqualByComparingTo("328.09");
	}

	@Test
	void commissionCanLeavePackagingOut() {
		PlatformTerms noPackaging = new PlatformTerms(0, 1, Platform.SWIGGY, bd("18"), bd("2"), bd("18"), bd("0"), bd("0.1"),
				false, bd("1"), null);
		assertThat(ReconciliationEngine.expectedFees(noPackaging, bd("450"), bd("20"), bd("40")).commission())
			.isEqualByComparingTo("73.80");
	}

	@Test
	void aCorrectStatementProducesNoFindings() {
		order("A1", OrderStatus.DELIVERED, "450", "20", "40");
		correctLine("A1", "450", "20", "40");
		order("A2", OrderStatus.DELIVERED, "300", "0", "0");
		correctLine("A2", "300", "0", "0");
		ReconciliationEngine.Result result = run();
		assertThat(result.findings()).isEmpty();
		assertThat(result.matchedLines()).isEqualTo(2);
	}

	@Test
	void flagsCommissionAboveTheContractIncludingGstOnTheExcess() {
		order("A1", OrderStatus.DELIVERED, "500", "0", "0");
		// 22% instead of 18% on ₹500: ₹110 instead of ₹90.
		ExpectedFees fees = ReconciliationEngine.expectedFees(this.terms, bd("500"), bd("0"), bd("0"));
		BigDecimal gst = Money.percent(bd("110").add(fees.pgFee()), bd("18"));
		line("A1", "Delivered", "500", "0", "0", "110", fees.pgFee().toPlainString(), gst.toPlainString(), "0.50", "0", "0", "0",
				"0", bd("500").subtract(Money.sum(bd("110"), fees.pgFee(), gst, bd("0.50"))).toPlainString());
		List<FindingDraft> findings = run().findings();
		assertThat(findings).extracting(FindingDraft::type).containsExactly(FindingType.COMMISSION_OVERCHARGE);
		assertThat(findings.get(0).amountAtRisk()).isEqualByComparingTo("23.60");
		assertThat(findings.get(0).expectedAmount()).isEqualByComparingTo("90.00");
	}

	@Test
	void flagsMissingOrdersButNotOnesPaidInAnotherStatement() {
		order("A1", OrderStatus.DELIVERED, "400", "0", "0");
		correctLine("A1", "400", "0", "0");
		order("A2", OrderStatus.DELIVERED, "300", "0", "0");
		order("A3", OrderStatus.DELIVERED, "200", "0", "0");
		order("A4", OrderStatus.CANCELLED, "250", "0", "0");
		ReconciliationEngine.Result result = ReconciliationEngine.run(input(Set.of("A3"), List.of()));
		assertThat(result.findings()).extracting(FindingDraft::type).containsExactly(FindingType.MISSING_ORDER);
		FindingDraft missing = result.findings().get(0);
		assertThat(missing.orderRef()).isEqualTo("A2");
		assertThat(missing.amountAtRisk())
			.isEqualByComparingTo(ReconciliationEngine.expectedFees(this.terms, bd("300"), bd("0"), bd("0")).netPayout());
	}

	@Test
	void flagsADeliveredOrderSettledAsCancelled() {
		order("A1", OrderStatus.DELIVERED, "600", "0", "0");
		line("A1", "Cancelled", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0");
		List<FindingDraft> findings = run().findings();
		assertThat(findings).extracting(FindingDraft::type).containsExactly(FindingType.STATUS_MISMATCH);
		assertThat(findings.get(0).severity()).isEqualTo(Severity.HIGH);
		assertThat(findings.get(0).amountAtRisk())
			.isEqualByComparingTo(ReconciliationEngine.expectedFees(this.terms, bd("600"), bd("0"), bd("0")).netPayout());
	}

	@Test
	void comparesOrderValueOnceAcrossTheOrdersRows() {
		order("A1", OrderStatus.DELIVERED, "500", "0", "0");
		correctLine("A1", "420", "0", "0");
		// A separate refund row for the same order must not count as a second short sale.
		line("A1", "Delivered", "0", "0", "0", "0", "0", "0", "0", "100", "0", "0", "0", "-100");
		List<FindingDraft> findings = run().findings();
		assertThat(findings).extracting(FindingDraft::type)
			.containsExactlyInAnyOrder(FindingType.VALUE_MISMATCH, FindingType.CUSTOMER_REFUND);
		FindingDraft value = findings.stream().filter((f) -> f.type() == FindingType.VALUE_MISMATCH).findFirst().orElseThrow();
		assertThat(value.amountAtRisk())
			.isEqualByComparingTo(ReconciliationEngine.expectedFees(this.terms, bd("80"), bd("0"), bd("0")).netPayout());
	}

	@Test
	void aRefundRecoveredTwiceIsOneRefundAndOneDuplicate() {
		order("A1", OrderStatus.DELIVERED, "400", "0", "0");
		correctLine("A1", "400", "0", "0");
		line("A1", "Delivered", "0", "0", "0", "0", "0", "0", "0", "150", "0", "0", "0", "-150");
		line("A1", "Delivered", "0", "0", "0", "0", "0", "0", "0", "150", "0", "0", "0", "-150");
		List<FindingDraft> findings = run().findings();
		assertThat(findings).extracting(FindingDraft::type)
			.containsExactlyInAnyOrder(FindingType.CUSTOMER_REFUND, FindingType.DUPLICATE_DEDUCTION);
		assertThat(findings).allSatisfy((f) -> assertThat(f.amountAtRisk()).isEqualByComparingTo("150.00"));
	}

	@Test
	void aRefundAlreadyTakenInAnEarlierStatementIsADuplicate() {
		order("A1", OrderStatus.DELIVERED, "400", "0", "0");
		line("A1", "Delivered", "0", "0", "0", "0", "0", "0", "0", "150", "0", "0", "0", "-150");
		StatementLine earlier = new StatementLine(99, 7, 1, "A1", START.minusDays(3), OrderStatus.DELIVERED, Money.ZERO,
				Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, bd("150"),
				Money.ZERO, Money.ZERO, Money.ZERO, bd("-150"), null);
		List<FindingDraft> findings = ReconciliationEngine.run(input(Set.of("A1"), List.of(earlier))).findings();
		assertThat(findings).extracting(FindingDraft::type).containsExactly(FindingType.DUPLICATE_DEDUCTION);
	}

	@Test
	void flagsDeductionsPenaltiesAndBadArithmetic() {
		order("A1", OrderStatus.CANCELLED, "300", "0", "0");
		line("A1", "Cancelled", "0", "0", "0", "0", "0", "0", "0", "0", "45", "0", "0", "-45");
		order("A2", OrderStatus.REJECTED, "300", "0", "0");
		line("A2", "Rejected", "0", "0", "0", "0", "0", "0", "0", "0", "0", "50", "0", "-50");
		order("A3", OrderStatus.DELIVERED, "200", "0", "0");
		ExpectedFees fees = ReconciliationEngine.expectedFees(this.terms, bd("200"), bd("0"), bd("0"));
		line("A3", "Delivered", "200", "0", "0", fees.commission().toPlainString(), fees.pgFee().toPlainString(),
				fees.gstOnFees().toPlainString(), fees.tds().toPlainString(), "0", "0", "0", "0",
				fees.netPayout().subtract(bd("30")).toPlainString());
		List<FindingDraft> findings = run().findings();
		assertThat(findings).extracting(FindingDraft::type)
			.containsExactlyInAnyOrder(FindingType.CANCELLATION_DEDUCTION, FindingType.PENALTY, FindingType.ARITHMETIC_ERROR);
		assertThat(findings).filteredOn((f) -> f.type() == FindingType.ARITHMETIC_ERROR)
			.singleElement()
			.satisfies((f) -> assertThat(f.amountAtRisk()).isEqualByComparingTo("30.00"));
	}

	@Test
	void separatesAdDeductionsFromUnexplainedOnes() {
		adjustment("-750", "Ads deduction: CPC campaign");
		adjustment("-299", "Miscellaneous recovery");
		adjustment("120", "Goodwill credit");
		List<FindingDraft> findings = run().findings();
		assertThat(findings).extracting(FindingDraft::type)
			.containsExactlyInAnyOrder(FindingType.AD_DEDUCTION, FindingType.UNEXPLAINED_ADJUSTMENT);
	}

	@Test
	void flagsABankCreditBelowTheStatement() {
		order("A1", OrderStatus.DELIVERED, "1000", "0", "0");
		correctLine("A1", "1000", "0", "0");
		this.credited = this.lines.get(0).netPayout().subtract(bd("400"));
		List<FindingDraft> findings = run().findings();
		assertThat(findings).extracting(FindingDraft::type).containsExactly(FindingType.CREDIT_SHORTFALL);
		assertThat(findings.get(0).amountAtRisk()).isEqualByComparingTo("400.00");
	}

	@Test
	void differencesWithinToleranceAreIgnored() {
		order("A1", OrderStatus.DELIVERED, "500", "0", "0");
		ExpectedFees fees = ReconciliationEngine.expectedFees(this.terms, bd("500"), bd("0"), bd("0"));
		BigDecimal commission = fees.commission().add(bd("0.90"));
		BigDecimal gst = Money.percent(commission.add(fees.pgFee()), bd("18"));
		line("A1", "Delivered", "500", "0", "0", commission.toPlainString(), fees.pgFee().toPlainString(), gst.toPlainString(),
				fees.tds().toPlainString(), "0", "0", "0", "0",
				bd("500").subtract(Money.sum(commission, fees.pgFee(), gst, fees.tds())).toPlainString());
		assertThat(run().findings()).isEmpty();
	}

	@Test
	void flagsTdsAtTheOldOnePercentRate() {
		order("A1", OrderStatus.DELIVERED, "1000", "0", "0");
		ExpectedFees fees = ReconciliationEngine.expectedFees(this.terms, bd("1000"), bd("0"), bd("0"));
		line("A1", "Delivered", "1000", "0", "0", fees.commission().toPlainString(), fees.pgFee().toPlainString(),
				fees.gstOnFees().toPlainString(), "10.00", "0", "0", "0", "0",
				bd("1000").subtract(Money.sum(fees.commission(), fees.pgFee(), fees.gstOnFees(), bd("10"))).toPlainString());
		List<FindingDraft> findings = run().findings();
		assertThat(findings).extracting(FindingDraft::type).containsExactly(FindingType.TDS_OVERDEDUCTED);
		assertThat(findings.get(0).amountAtRisk()).isEqualByComparingTo("9.00");
	}

	@Test
	void fingerprintsAreStableAcrossRuns() {
		order("A1", OrderStatus.DELIVERED, "300", "0", "0");
		order("A2", OrderStatus.DELIVERED, "300", "0", "0");
		correctLine("A1", "300", "0", "0");
		assertThat(run().findings()).extracting(FindingDraft::fingerprint).containsExactly("MISSING_ORDER:A2");
		assertThat(run().findings()).extracting(FindingDraft::fingerprint).containsExactly("MISSING_ORDER:A2");
	}

	private ReconciliationEngine.Result run() {
		return ReconciliationEngine.run(input(Set.of(), List.of()));
	}

	private ReconciliationEngine.Input input(Set<String> settledElsewhere, List<StatementLine> prior) {
		List<OrderRecord> delivered = this.orders.values()
			.stream()
			.filter((o) -> o.status() == OrderStatus.DELIVERED)
			.sorted((a, b) -> a.orderRef().compareTo(b.orderRef()))
			.toList();
		return new ReconciliationEngine.Input(START, END, this.credited, this.terms, this.lines, this.orders, delivered,
				settledElsewhere, prior);
	}

	private void order(String ref, OrderStatus status, String items, String packaging, String discount) {
		this.orders.put(ref, new OrderRecord(0, 1, Platform.SWIGGY, ref, LocalDateTime.of(2026, 10, 14, 13, 0), status, bd(items),
				bd(packaging), bd(discount)));
	}

	private void correctLine(String ref, String items, String packaging, String discount) {
		ExpectedFees fees = ReconciliationEngine.expectedFees(this.terms, bd(items), bd(packaging), bd(discount));
		line(ref, "Delivered", items, packaging, discount, fees.commission().toPlainString(), fees.pgFee().toPlainString(),
				fees.gstOnFees().toPlainString(), fees.tds().toPlainString(), "0", "0", "0", "0",
				fees.netPayout().toPlainString());
	}

	private void line(String ref, String status, String items, String packaging, String discount, String commission, String pg,
			String gst, String tds, String refund, String cancellation, String penalty, String adjustment, String net) {
		int lineNo = this.lines.size() + 1;
		this.lines.add(new StatementLine(lineNo, 1, lineNo, ref, LocalDate.of(2026, 10, 14), OrderStatus.parse(status), bd(items),
				bd(packaging), bd(discount), bd(commission), bd(pg), Money.ZERO, bd(gst), Money.ZERO, bd(tds), bd(refund),
				bd(cancellation), bd(penalty), bd(adjustment), bd(net), null));
	}

	private void adjustment(String amount, String description) {
		int lineNo = this.lines.size() + 1;
		this.lines.add(new StatementLine(lineNo, 1, lineNo, null, null, null, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO,
				Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, bd(amount),
				bd(amount), description));
	}

	private static BigDecimal bd(String value) {
		return Money.of(value);
	}

}
