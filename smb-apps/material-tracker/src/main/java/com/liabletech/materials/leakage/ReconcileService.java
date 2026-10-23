package com.liabletech.materials.leakage;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liabletech.materials.bill.Bill;
import com.liabletech.materials.bill.BillLine;
import com.liabletech.materials.bill.Bills;
import com.liabletech.materials.catalog.Material;
import com.liabletech.materials.catalog.Materials;
import com.liabletech.materials.delivery.Deliveries;
import com.liabletech.materials.delivery.Delivery;
import com.liabletech.materials.delivery.DeliveryLine;
import com.liabletech.materials.supplier.Suppliers;

/**
 * Checks a bill: finds the deliveries it covers (by the challan numbers it quotes, or else the
 * supplier's unbilled deliveries to that site in the 45 days before the bill), runs the
 * {@link Reconciler}, and stores the findings.
 */
@Service
public class ReconcileService {

	/** Without challan numbers, a bill is matched to deliveries from this many days before it. */
	static final int WINDOW_DAYS = 45;

	private final Bills bills;

	private final Deliveries deliveries;

	private final Materials materials;

	private final Suppliers suppliers;

	private final Flags flags;

	private final Clock clock;

	public ReconcileService(Bills bills, Deliveries deliveries, Materials materials, Suppliers suppliers, Flags flags,
			Clock clock) {
		this.bills = bills;
		this.deliveries = deliveries;
		this.materials = materials;
		this.suppliers = suppliers;
		this.flags = flags;
		this.clock = clock;
	}

	@Transactional
	public Result check(long accountId, long billId) {
		Bill bill = bills.get(accountId, billId);
		if (bill.supplierId() == null) {
			throw new IllegalStateException("Choose the supplier before checking the bill.");
		}
		List<BillLine> lines = bills.lines(billId);
		LocalDate onDate = bill.billDate() != null ? bill.billDate() : LocalDate.now(clock);

		Set<String> refs = new LinkedHashSet<>(bill.challanRefList());
		lines.stream().map(BillLine::challanRef).filter(Objects::nonNull).forEach(refs::add);
		Map<Long, Delivery> linked = new LinkedHashMap<>();
		List<String> notFound = new ArrayList<>();
		if (!refs.isEmpty()) {
			for (String ref : refs) {
				List<Delivery> found = deliveries.byChallan(accountId, bill.supplierId(), ref)
					.stream()
					.filter((d) -> Reconciler.sameChallan(d.challanNumber(), ref))
					.toList();
				if (found.isEmpty()) {
					notFound.add(ref);
				}
				found.forEach((d) -> linked.putIfAbsent(d.id(), d));
			}
		}
		else {
			deliveries.unbilledFor(accountId, bill.supplierId(), bill.siteId(), onDate.minusDays(WINDOW_DAYS), onDate, billId)
				.forEach((d) -> linked.putIfAbsent(d.id(), d));
		}
		List<Long> linkedIds = List.copyOf(linked.keySet());
		bills.linkDeliveries(billId, linkedIds);

		Map<Long, String> billedElsewhere = bills.billedElsewhere(billId, linkedIds)
			.stream()
			.collect(Collectors.toMap(Bills.BilledElsewhere::deliveryId, Bills.BilledElsewhere::billNumber, (a, b) -> a));
		List<Reconciler.LinkedDelivery> linkedDeliveries = linked.values()
			.stream()
			.map((d) -> new Reconciler.LinkedDelivery(d, deliveries.lines(d.id())))
			.toList();
		Map<Long, Material> materialMap = materials.all(accountId)
			.stream()
			.collect(Collectors.toMap(Material::id, Function.identity()));
		List<FlagDraft> drafts = Reconciler.check(new Reconciler.Input(bill, lines, linkedDeliveries, materialMap,
				suppliers.rates(accountId, bill.supplierId()), onDate,
				bills.duplicateNumber(accountId, billId, bill.supplierId(), bill.billNumber()), billedElsewhere, notFound));

		flags.deleteOpenForBill(accountId, billId);
		List<Flag> settled = flags.forBill(accountId, billId);
		OffsetDateTime now = OffsetDateTime.now(clock);
		BigDecimal atRisk = BigDecimal.ZERO;
		int stored = 0;
		for (FlagDraft draft : drafts) {
			boolean alreadyHandled = settled.stream()
				.anyMatch((f) -> f.kind().equals(draft.kind()) && Objects.equals(f.materialId(), draft.materialId())
						&& Objects.equals(f.deliveryId(), draft.deliveryId()));
			if (!alreadyHandled) {
				flags.insert(accountId, bill.siteId(), bill.supplierId(), billId, null, draft, now);
				atRisk = atRisk.add(draft.amount());
				stored++;
			}
		}
		settleGateShortages(accountId, bill, lines, linkedIds, drafts, now);
		bills.markChecked(billId, now);
		return new Result(linkedIds.size(), stored, atRisk);
	}

	/**
	 * Gate shortages on the covered deliveries are settled by the bill: either the over-billing
	 * finding now carries the money, or the supplier billed only what was counted.
	 */
	private void settleGateShortages(long accountId, Bill bill, List<BillLine> lines, List<Long> deliveryIds,
			List<FlagDraft> drafts, OffsetDateTime now) {
		Set<Long> billedMaterials = lines.stream()
			.map(BillLine::materialId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		Set<Long> overbilled = drafts.stream()
			.filter((d) -> Flag.QTY_OVERBILLED.equals(d.kind()))
			.map(FlagDraft::materialId)
			.collect(Collectors.toSet());
		for (Flag shortage : flags.openShortages(accountId, deliveryIds)) {
			if (!billedMaterials.contains(shortage.materialId())) {
				continue;
			}
			String note = overbilled.contains(shortage.materialId())
					? "Counted in the check of bill " + bill.billNumber() + "."
					: "Bill " + bill.billNumber() + " charges only what was counted.";
			flags.setStatus(accountId, shortage.id(), Flag.CLOSED, note, now);
		}
	}

	public record Result(int deliveriesLinked, int findings, BigDecimal atRisk) {
	}

	/** For the bill page: the deliveries a bill was checked against, with their lines. */
	public List<Reconciler.LinkedDelivery> linked(long accountId, long billId) {
		List<Reconciler.LinkedDelivery> result = new ArrayList<>();
		for (Long id : bills.linkedDeliveryIds(billId)) {
			Delivery delivery = deliveries.get(accountId, id);
			List<DeliveryLine> lines = deliveries.lines(id);
			result.add(new Reconciler.LinkedDelivery(delivery, lines));
		}
		return result;
	}

}
