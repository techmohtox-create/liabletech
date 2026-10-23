package com.liabletech.materials.leakage;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.liabletech.materials.bill.Bill;
import com.liabletech.materials.bill.BillLine;
import com.liabletech.materials.catalog.Material;
import com.liabletech.materials.catalog.Unit;
import com.liabletech.materials.delivery.Delivery;
import com.liabletech.materials.delivery.DeliveryLine;
import com.liabletech.materials.supplier.RateAgreement;
import com.liabletech.platform.text.Fuzzy;
import com.liabletech.platform.text.IndianMoney;

/**
 * Compares a supplier bill with the deliveries it covers and the agreed rates. Pure logic: the
 * caller loads everything and stores the findings.
 *
 * <p>
 * Money is counted once. The right amount for a bill is what was received at the agreed rate;
 * the difference splits exactly into over-billed quantity (at the agreed rate) plus the rate excess
 * on everything billed, plus any arithmetic padding. Duplicate challans, duplicate bill numbers and
 * unknown challans explain where an over-billing came from, so they carry no amount of their own.
 */
public final class Reconciler {

	private static final MathContext MATH = MathContext.DECIMAL64;

	private Reconciler() {
	}

	public record LinkedDelivery(Delivery delivery, List<DeliveryLine> lines) {
	}

	/**
	 * @param onDate the date rates are looked up for: the bill date, or today without one
	 * @param billedElsewhere delivery id to the number of another bill that already charged for it
	 * @param challansNotFound challan numbers the bill quotes that match no recorded delivery
	 */
	public record Input(Bill bill, List<BillLine> lines, List<LinkedDelivery> deliveries, Map<Long, Material> materials,
			List<RateAgreement> rates, LocalDate onDate, boolean duplicateBillNumber, Map<Long, String> billedElsewhere,
			List<String> challansNotFound) {
	}

	public static List<FlagDraft> check(Input in) {
		List<FlagDraft> out = new ArrayList<>();
		if (in.duplicateBillNumber()) {
			out.add(new FlagDraft(Flag.DUPLICATE_BILL, null, null, "Bill number " + in.bill().billNumber()
					+ " from this supplier is already entered. If it is the same bill, reject this copy.", null, null, null,
					BigDecimal.ZERO));
		}
		for (String challan : in.challansNotFound()) {
			out.add(new FlagDraft(Flag.CHALLAN_NOT_FOUND, null, null, "The bill quotes challan " + challan
					+ ", but no delivery with that challan was recorded. Ask the site whether it arrived.", null, null, null,
					BigDecimal.ZERO));
		}
		for (Map.Entry<Long, String> entry : in.billedElsewhere().entrySet()) {
			String challan = in.deliveries()
				.stream()
				.filter((d) -> d.delivery().id().equals(entry.getKey()))
				.map((d) -> d.delivery().challanNumber())
				.findFirst()
				.orElse("?");
			out.add(new FlagDraft(Flag.DUPLICATE_CHALLAN, null, entry.getKey(), "Challan " + challan
					+ " was already charged on bill " + entry.getValue() + "; it is not counted again here.", null, null, null,
					BigDecimal.ZERO));
		}
		arithmetic(in, out);
		quantitiesAndRates(in, out);
		return out;
	}

	private static void arithmetic(Input in, List<FlagDraft> out) {
		BigDecimal lineSum = BigDecimal.ZERO;
		for (BillLine line : in.lines()) {
			if (line.amount() != null) {
				lineSum = lineSum.add(line.amount());
			}
			if (line.qty() == null || line.rate() == null || line.amount() == null) {
				continue;
			}
			BigDecimal expected = line.qty().multiply(line.rate()).setScale(2, RoundingMode.HALF_UP);
			BigDecimal excess = line.amount().subtract(expected);
			if (excess.compareTo(threshold(line.amount())) > 0) {
				out.add(new FlagDraft(Flag.ARITHMETIC, line.materialId(), null,
						"Line %d: %s × %s = %s, but the bill says %s.".formatted(line.lineNo(), qty(line.qty()),
								rupees(line.rate()), rupees(expected), rupees(line.amount())),
						null, null, null, excess));
			}
		}
		Bill bill = in.bill();
		if (bill.taxableAmount() != null && !in.lines().isEmpty()) {
			BigDecimal excess = bill.taxableAmount().subtract(lineSum);
			if (excess.compareTo(threshold(bill.taxableAmount())) > 0) {
				out.add(new FlagDraft(Flag.ARITHMETIC, null, null, "The taxable total " + rupees(bill.taxableAmount())
						+ " is more than the lines add up to (" + rupees(lineSum) + ").", null, null, null, excess));
			}
		}
		if (bill.totalAmount() != null && bill.taxableAmount() != null && bill.taxAmount() != null) {
			BigDecimal excess = bill.totalAmount().subtract(bill.taxableAmount().add(bill.taxAmount()));
			if (excess.compareTo(BigDecimal.ONE) > 0) {
				out.add(new FlagDraft(Flag.ARITHMETIC, null, null, "The grand total " + rupees(bill.totalAmount())
						+ " is more than taxable amount plus GST (" + rupees(bill.taxableAmount().add(bill.taxAmount())) + ").",
						null, null, null, excess));
			}
		}
	}

	private static void quantitiesAndRates(Input in, List<FlagDraft> out) {
		Map<Long, BigDecimal> billedBase = new LinkedHashMap<>();
		Map<Long, BigDecimal> billedValue = new LinkedHashMap<>();
		for (BillLine line : in.lines()) {
			Material material = line.materialId() == null ? null : in.materials().get(line.materialId());
			if (material == null) {
				out.add(new FlagDraft(Flag.NEEDS_CHECK, null, null, "Line %d \"%s\" is not matched to a material, so it was not checked."
					.formatted(line.lineNo(), line.description()), null, null, null, BigDecimal.ZERO));
				continue;
			}
			BigDecimal base = Unit.convert(line.qty(), line.parsedUnit(), material.baseUnit(), material);
			if (base == null) {
				out.add(new FlagDraft(Flag.NEEDS_CHECK, material.id(), null, "Line %d: %s %s of %s cannot be compared in %s."
					.formatted(line.lineNo(), qty(line.qty()), line.unit() == null ? "(no unit)" : line.unit(), material.name(),
							material.baseUnit().label()), null, null, null, BigDecimal.ZERO));
				continue;
			}
			billedBase.merge(material.id(), base, BigDecimal::add);
			BigDecimal value = line.rate() != null ? line.qty().multiply(line.rate()) : line.amount();
			if (value != null) {
				billedValue.merge(material.id(), value, BigDecimal::add);
			}
			rate(in, line, material).ifPresent(out::add);
		}
		Map<Long, BigDecimal> received = new LinkedHashMap<>();
		for (LinkedDelivery linked : in.deliveries()) {
			if (in.billedElsewhere().containsKey(linked.delivery().id())) {
				continue;
			}
			for (DeliveryLine line : linked.lines()) {
				Material material = line.materialId() == null ? null : in.materials().get(line.materialId());
				if (material == null) {
					continue;
				}
				BigDecimal counted = line.receivedQty() != null ? line.receivedQty() : line.slipQty();
				BigDecimal base = Unit.convert(counted, line.parsedUnit(), material.baseUnit(), material);
				if (base != null) {
					received.merge(material.id(), base, BigDecimal::add);
				}
			}
		}
		for (Map.Entry<Long, BigDecimal> entry : billedBase.entrySet()) {
			Material material = in.materials().get(entry.getKey());
			BigDecimal billed = entry.getValue();
			BigDecimal got = received.getOrDefault(material.id(), BigDecimal.ZERO);
			BigDecimal allowed = got.multiply(BigDecimal.ONE.add(material.tolerancePct().movePointLeft(2)));
			if (billed.compareTo(allowed.add(new BigDecimal("0.0005"))) <= 0) {
				continue;
			}
			BigDecimal over = billed.subtract(got);
			BigDecimal unitRate = agreedPerBase(in, material)
				.orElseGet(() -> billedValue.containsKey(material.id())
						? billedValue.get(material.id()).divide(billed, MATH) : BigDecimal.ZERO);
			BigDecimal amount = over.multiply(unitRate).setScale(2, RoundingMode.HALF_UP);
			Unit unit = material.baseUnit();
			String message = got.signum() == 0
					? "Billed %s %s of %s, but no unbilled delivery of it is linked to this bill.".formatted(qty(billed),
							unit.label(billed), material.name())
					: "Billed %s %s of %s, but %s %s arrived on the linked challans: %s %s more than received.".formatted(
							qty(billed), unit.label(billed), material.name(), qty(got), unit.label(got), qty(over),
							unit.label(over));
			out.add(new FlagDraft(Flag.QTY_OVERBILLED, material.id(), null, message, got, billed, material.baseUnit().name(),
					amount));
		}
	}

	private static Optional<FlagDraft> rate(Input in, BillLine line, Material material) {
		if (line.rate() == null || line.qty() == null) {
			return Optional.empty();
		}
		Optional<RateAgreement> agreed = agreement(in, material);
		if (agreed.isEmpty()) {
			return Optional.empty();
		}
		BigDecimal perAgreedUnit = Unit.convert(BigDecimal.ONE, line.parsedUnit(), agreed.get().rateUnit(), material);
		if (perAgreedUnit == null) {
			return Optional.empty();
		}
		BigDecimal agreedForLineUnit = agreed.get().rate().multiply(perAgreedUnit).setScale(2, RoundingMode.HALF_UP);
		BigDecimal excessPerUnit = line.rate().subtract(agreedForLineUnit);
		if (excessPerUnit.compareTo(agreedForLineUnit.multiply(new BigDecimal("0.005")).max(new BigDecimal("0.01"))) <= 0) {
			return Optional.empty();
		}
		BigDecimal amount = excessPerUnit.multiply(line.qty()).setScale(2, RoundingMode.HALF_UP);
		return Optional.of(new FlagDraft(Flag.RATE_ABOVE_AGREED, material.id(), null,
				"Line %d: %s billed at %s per %s; agreed rate is %s.".formatted(line.lineNo(), material.name(),
						rupees(line.rate()), line.parsedUnit().label(), rupees(agreedForLineUnit)),
				null, null, null, amount));
	}

	private static Optional<BigDecimal> agreedPerBase(Input in, Material material) {
		return agreedRatePerBase(in.rates(), in.bill().supplierId(), material, in.onDate());
	}

	private static Optional<RateAgreement> agreement(Input in, Material material) {
		return agreement(in.rates(), in.bill().supplierId(), material, in.onDate());
	}

	/** The agreed rate per unit of the material's own unit, if one applies on the date. */
	public static Optional<BigDecimal> agreedRatePerBase(List<RateAgreement> rates, Long supplierId, Material material,
			LocalDate date) {
		return agreement(rates, supplierId, material, date).map((agreed) -> {
			BigDecimal perAgreedUnit = Unit.convert(BigDecimal.ONE, material.baseUnit(), agreed.rateUnit(), material);
			return perAgreedUnit == null ? null : agreed.rate().multiply(perAgreedUnit);
		});
	}

	private static Optional<RateAgreement> agreement(List<RateAgreement> rates, Long supplierId, Material material,
			LocalDate date) {
		if (supplierId == null) {
			return Optional.empty();
		}
		return rates.stream()
			.filter((r) -> r.materialId().equals(material.id()) && r.supplierId().equals(supplierId) && r.validOn(date))
			.findFirst();
	}

	/** Rounding (to the rupee, or of a rate printed with fewer decimals) is not worth a dispute. */
	private static BigDecimal threshold(BigDecimal amount) {
		return amount.abs().multiply(new BigDecimal("0.001")).max(BigDecimal.ONE);
	}

	/** Whether two challan numbers are the same, as written by different people. */
	public static boolean sameChallan(String a, String b) {
		return Fuzzy.sameCode(a, b);
	}

	static String qty(BigDecimal value) {
		BigDecimal rounded = value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
		return IndianMoney.group(rounded, Math.max(0, rounded.scale()));
	}

	static String rupees(BigDecimal value) {
		return IndianMoney.rupees(value);
	}

	/** Statuses whose findings a re-check must not duplicate. */
	static final Set<String> SETTLED = Set.of(Flag.RAISED, Flag.RECOVERED, Flag.DISMISSED, Flag.CLOSED);

}
