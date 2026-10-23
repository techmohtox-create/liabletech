package com.liabletech.materials;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.liabletech.materials.bill.Bill;
import com.liabletech.materials.bill.BillLine;
import com.liabletech.materials.catalog.Material;
import com.liabletech.materials.catalog.MaterialMatcher;
import com.liabletech.materials.catalog.Unit;
import com.liabletech.materials.delivery.CountReply;
import com.liabletech.materials.delivery.Delivery;
import com.liabletech.materials.delivery.DeliveryLine;
import com.liabletech.materials.leakage.Flag;
import com.liabletech.materials.leakage.FlagDraft;
import com.liabletech.materials.leakage.Reconciler;
import com.liabletech.materials.supplier.RateAgreement;

class MaterialRulesTests {

	private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

	private static final long SUPPLIER = 7L;

	private static final Material OPC = material(1, "Cement OPC 53", "BAG", "opc 53, opc53, ultratech opc", "50", null, "0");

	private static final Material PPC = material(2, "Cement PPC", "BAG", "ppc, pozzolana", "50", null, "0");

	private static final Material TMT12 = material(3, "TMT bar 12 mm", "KG", "tmt, tmt bar, fe500, fe500d", null, 12, "2");

	private static final Material TMT16 = material(4, "TMT bar 16 mm", "KG", "tmt, tmt bar, fe500, fe500d", null, 16, "2");

	private static final Material SAND = material(5, "River sand", "CFT", "river sand, reti", null, null, "5");

	private static final List<Material> CATALOG = List.of(OPC, PPC, TMT12, TMT16, SAND);

	@Test
	void cementInTonnesConvertsToBagsThroughTheBagWeight() {
		assertThat(Unit.convert(new BigDecimal("10"), Unit.MT, Unit.BAG, OPC)).isEqualByComparingTo("200");
		assertThat(Unit.convert(new BigDecimal("40"), Unit.BAG, Unit.KG, OPC)).isEqualByComparingTo("2000");
	}

	@Test
	void sandVolumesConvertThroughCubicFeet() {
		assertThat(Unit.convert(new BigDecimal("2"), Unit.BRASS, Unit.CFT, SAND)).isEqualByComparingTo("200");
		assertThat(Unit.convert(BigDecimal.ONE, Unit.CUM, Unit.CFT, SAND)).isEqualByComparingTo("35.3147");
		assertThat(Unit.convert(new BigDecimal("150"), Unit.CFT, Unit.BRASS, SAND)).isEqualByComparingTo("1.5");
	}

	@Test
	void barsCountedConvertToKgByTheSiteFormula() {
		// D²/162 kg per metre, 12 m bars: 144 / 162 × 12 = 10.667 kg.
		assertThat(Unit.barKg(12)).isEqualByComparingTo("10.666668");
		assertThat(Unit.convert(new BigDecimal("100"), Unit.NOS, Unit.KG, TMT12)).isEqualByComparingTo("1066.6668");
		assertThat(Unit.convert(new BigDecimal("100"), Unit.NOS, Unit.KG, OPC)).as("cement is not counted in bars").isNull();
	}

	@Test
	void unrelatedUnitsDoNotConvert() {
		assertThat(Unit.convert(new BigDecimal("5"), Unit.LTR, Unit.BAG, OPC)).isNull();
		assertThat(Unit.convert(new BigDecimal("5"), Unit.CFT, Unit.KG, SAND)).isNull();
	}

	@Test
	void unitsAreReadAsSuppliersWriteThem() {
		assertThat(Unit.parse("Bags")).isEqualTo(Unit.BAG);
		assertThat(Unit.parse("M.T.")).isEqualTo(Unit.MT);
		assertThat(Unit.parse("Cu.M")).isEqualTo(Unit.CUM);
		assertThat(Unit.parse("Nos.")).isEqualTo(Unit.NOS);
		assertThat(Unit.parse("brass")).isEqualTo(Unit.BRASS);
		assertThat(Unit.parse("trips")).isNull();
	}

	@Test
	void slipLinesMatchTheCatalogueByAliasAndBarSize() {
		assertThat(MaterialMatcher.match("UltraTech OPC 53 Grade", CATALOG)).contains(OPC);
		assertThat(MaterialMatcher.match("PPC cement", CATALOG)).contains(PPC);
		assertThat(MaterialMatcher.match("TMT Fe500D 12mm", CATALOG)).contains(TMT12);
		assertThat(MaterialMatcher.match("Fe500 16 mm bars", CATALOG)).contains(TMT16);
		assertThat(MaterialMatcher.match("TMT bars", CATALOG)).as("size unknown: a person picks").isEmpty();
		assertThat(MaterialMatcher.match("Reti", CATALOG)).contains(SAND);
		assertThat(MaterialMatcher.match("Wall putty", CATALOG)).isEmpty();
	}

	@Test
	void supervisorRepliesAreUnderstood() {
		assertThat(CountReply.parse("OK", 2)).hasValueSatisfying((r) -> assertThat(r.allAsSlip()).isTrue());
		assertThat(CountReply.parse("Haan!", 2)).hasValueSatisfying((r) -> assertThat(r.allAsSlip()).isTrue());
		assertThat(CountReply.parse("1=195, 2-1.2", 2)).hasValueSatisfying((r) -> {
			assertThat(r.allAsSlip()).isFalse();
			assertThat(r.counts()).containsOnlyKeys(1, 2);
			assertThat(r.counts().get(2)).isEqualByComparingTo("1.2");
		});
		assertThat(CountReply.parse("195 bags", 1)).hasValueSatisfying(
				(r) -> assertThat(r.counts().get(1)).isEqualByComparingTo("195"));
		assertThat(CountReply.parse("195", 2)).as("which line?").isEmpty();
		assertThat(CountReply.parse("3=10", 2)).as("no such line").isEmpty();
		assertThat(CountReply.parse("truck abhi aaya nahi", 1)).isEmpty();
	}

	@Test
	void overBillingAndRateAreValuedSoTheMoneyIsCountedOnce() {
		Reconciler.Input input = input(bill(null, null, null),
				List.of(billLine(1, OPC, "200", "BAG", "405", "81000")),
				List.of(delivery(11, "4401", deliveryLine(OPC, "200", "BAG", "195"))), rate(OPC, "390", Unit.BAG));

		List<FlagDraft> findings = Reconciler.check(input);

		assertThat(findings).extracting(FlagDraft::kind).containsExactlyInAnyOrder(Flag.QTY_OVERBILLED,
				Flag.RATE_ABOVE_AGREED);
		// 200 × 405 billed against 195 × 390 owed: ₹4,950, split without overlap.
		assertThat(amount(findings, Flag.QTY_OVERBILLED)).isEqualByComparingTo("1950");
		assertThat(amount(findings, Flag.RATE_ABOVE_AGREED)).isEqualByComparingTo("3000");
	}

	@Test
	void differenceWithinToleranceIsNotLeakage() {
		Reconciler.Input input = input(bill(null, null, null), List.of(billLine(1, SAND, "104", "CFT", "55", "5720")),
				List.of(delivery(11, "2231", deliveryLine(SAND, "100", "CFT", null))), rate(SAND, "55", Unit.CFT));

		assertThat(Reconciler.check(input)).isEmpty();
	}

	@Test
	void rateAgreedPerTonneIsComparedWithARatePerKg() {
		Reconciler.Input input = input(bill(null, null, null),
				List.of(billLine(1, TMT12, "1000", "KG", "61.50", "61500")),
				List.of(delivery(11, "M-778", deliveryLine(TMT12, "1000", "KG", null))), rate(TMT12, "60000", Unit.MT));

		List<FlagDraft> findings = Reconciler.check(input);

		assertThat(findings).singleElement().satisfies((f) -> {
			assertThat(f.kind()).isEqualTo(Flag.RATE_ABOVE_AGREED);
			assertThat(f.amount()).isEqualByComparingTo("1500");
		});
	}

	@Test
	void totalsThatDoNotAddUpAreFlagged() {
		Reconciler.Input input = input(bill("10000", "1800", "12300"),
				List.of(billLine(1, OPC, "20", "BAG", "390", "7800"), billLine(2, PPC, "5", "BAG", "370", "1850")),
				List.of(delivery(11, "4401", deliveryLine(OPC, "20", "BAG", null), deliveryLine(PPC, "5", "BAG", null))));

		List<FlagDraft> findings = Reconciler.check(input);

		assertThat(findings).extracting(FlagDraft::kind).containsExactly(Flag.ARITHMETIC, Flag.ARITHMETIC);
		assertThat(findings.get(0).amount()).as("taxable 10,000 vs lines 9,650").isEqualByComparingTo("350");
		assertThat(findings.get(1).amount()).as("12,300 vs 10,000 + 1,800").isEqualByComparingTo("500");
	}

	@Test
	void unmatchedLinesAndUnknownChallansAreFlaggedWithoutAnAmount() {
		BillLine unmatched = new BillLine(2L, 99L, 2, null, "Waterproofing compound", BigDecimal.TEN, "LTR",
				new BigDecimal("300"), new BigDecimal("3000"), null);
		Reconciler.Input input = new Reconciler.Input(bill(null, null, null),
				List.of(billLine(1, OPC, "20", "BAG", "390", "7800"), unmatched),
				List.of(delivery(11, "4401", deliveryLine(OPC, "20", "BAG", null))), materials(), List.of(), DAY, false,
				Map.of(), List.of("4499"));

		List<FlagDraft> findings = Reconciler.check(input);

		assertThat(findings).extracting(FlagDraft::kind).containsExactlyInAnyOrder(Flag.CHALLAN_NOT_FOUND,
				Flag.NEEDS_CHECK);
		assertThat(findings).allSatisfy((f) -> assertThat(f.amount()).isZero());
	}

	@Test
	void challanAlreadyBilledIsNotCountedAsReceivedAgain() {
		Reconciler.Input input = new Reconciler.Input(bill(null, null, null),
				List.of(billLine(1, OPC, "100", "BAG", "390", "39000")),
				List.of(delivery(11, "4401", deliveryLine(OPC, "100", "BAG", null))), materials(),
				List.of(rate(OPC, "390", Unit.BAG)), DAY, false, Map.of(11L, "SBCT/1187"), List.of());

		List<FlagDraft> findings = Reconciler.check(input);

		assertThat(findings).extracting(FlagDraft::kind).containsExactlyInAnyOrder(Flag.DUPLICATE_CHALLAN,
				Flag.QTY_OVERBILLED);
		assertThat(amount(findings, Flag.QTY_OVERBILLED)).isEqualByComparingTo("39000");
		assertThat(amount(findings, Flag.DUPLICATE_CHALLAN)).isZero();
	}

	@Test
	void challanNumbersMatchAsWrittenByDifferentPeople() {
		assertThat(Reconciler.sameChallan("DC-4401", "4401")).isTrue();
		assertThat(Reconciler.sameChallan("M/778", "M-778")).isTrue();
		assertThat(Reconciler.sameChallan("4401", "4410")).isFalse();
	}

	private static Reconciler.Input input(Bill bill, List<BillLine> lines, List<Reconciler.LinkedDelivery> deliveries,
			RateAgreement... rates) {
		return new Reconciler.Input(bill, lines, deliveries, materials(), List.of(rates), DAY, false, Map.of(), List.of());
	}

	private static Map<Long, Material> materials() {
		return CATALOG.stream().collect(Collectors.toMap(Material::id, Function.identity()));
	}

	private static Material material(long id, String name, String unit, String aliases, String bagKg, Integer bar,
			String tolerance) {
		return new Material(id, 1L, name, "X", unit, aliases, bagKg == null ? null : new BigDecimal(bagKg), bar,
				new BigDecimal(tolerance), true);
	}

	private static Bill bill(String taxable, String tax, String total) {
		return new Bill(99L, 1L, 1L, SUPPLIER, null, "UPLOAD", "READ", null, null, "B-1", DAY, null,
				taxable == null ? null : new BigDecimal(taxable), tax == null ? null : new BigDecimal(tax),
				total == null ? null : new BigDecimal(total), null, Bill.NEEDS_REVIEW, null, null);
	}

	private static BillLine billLine(int no, Material material, String qty, String unit, String rate, String amount) {
		return new BillLine((long) no, 99L, no, material.id(), material.name(), new BigDecimal(qty), unit,
				new BigDecimal(rate), new BigDecimal(amount), null);
	}

	private static Reconciler.LinkedDelivery delivery(long id, String challan, DeliveryLine... lines) {
		Delivery delivery = new Delivery(id, 1L, 1L, SUPPLIER, null, null, "WHATSAPP", null, "READ", null, null, challan,
				DAY.minusDays(3), null, null, Delivery.CONFIRMED, null, null, null);
		return new Reconciler.LinkedDelivery(delivery, List.of(lines));
	}

	private static DeliveryLine deliveryLine(Material material, String slip, String unit, String counted) {
		return new DeliveryLine(1L, 11L, 1, material.id(), material.name(), new BigDecimal(slip), unit,
				counted == null ? null : new BigDecimal(counted));
	}

	private static RateAgreement rate(Material material, String rate, Unit unit) {
		return new RateAgreement(1L, 1L, SUPPLIER, material.id(), new BigDecimal(rate), unit.name(), DAY.minusMonths(3),
				null);
	}

	private static BigDecimal amount(List<FlagDraft> findings, String kind) {
		return findings.stream()
			.filter((f) -> f.kind().equals(kind))
			.map(FlagDraft::amount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

}
