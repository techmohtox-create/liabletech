package com.liabletech.materials.delivery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liabletech.materials.bill.BillService;
import com.liabletech.materials.catalog.Material;
import com.liabletech.materials.catalog.MaterialMatcher;
import com.liabletech.materials.catalog.Materials;
import com.liabletech.materials.catalog.Unit;
import com.liabletech.materials.leakage.Flag;
import com.liabletech.materials.leakage.FlagDraft;
import com.liabletech.materials.leakage.Flags;
import com.liabletech.materials.leakage.Reconciler;
import com.liabletech.materials.reading.DocumentReader;
import com.liabletech.materials.site.SiteMember;
import com.liabletech.materials.site.Sites;
import com.liabletech.materials.supplier.RateAgreement;
import com.liabletech.materials.supplier.Supplier;
import com.liabletech.materials.supplier.Suppliers;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.text.IndianMoney;

/**
 * A delivery from photo to confirmed count. The slip is read, its lines matched to materials, and
 * the supervisor is asked on WhatsApp to confirm what actually arrived. Anything counted short is
 * recorded at once, valued at the agreed rate.
 */
@Service
public class DeliveryService {

	static final double READABLE_CONFIDENCE = 0.5;

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

	private final Deliveries deliveries;

	private final DocumentReader reader;

	private final Materials materials;

	private final Suppliers suppliers;

	private final Sites sites;

	private final BillService bills;

	private final Flags flags;

	private final Clock clock;

	public DeliveryService(Deliveries deliveries, DocumentReader reader, Materials materials, Suppliers suppliers,
			Sites sites, BillService bills, Flags flags, Clock clock) {
		this.deliveries = deliveries;
		this.reader = reader;
		this.materials = materials;
		this.suppliers = suppliers;
		this.sites = sites;
		this.bills = bills;
		this.flags = flags;
		this.clock = clock;
	}

	/**
	 * Reads a photographed slip and records the delivery. A photographed bill goes to the bills
	 * instead. From a supervisor on WhatsApp the delivery waits for their count; an upload from the
	 * office waits for the office to enter it.
	 */
	public Received receive(long accountId, long siteId, SiteMember member, StoredFile file, String caption,
			String source) {
		sites.get(accountId, siteId);
		return record(accountId, siteId, member, file, caption, source, reader.read(accountId, file, caption));
	}

	/** Records a delivery (or bill) from a reading already made, e.g. an import or demo data. */
	public Received record(long accountId, long siteId, SiteMember member, StoredFile file, String caption,
			String source, DocumentReader.Reading reading) {
		if (reading.read() && reading.isBill()) {
			long billId = bills.createFromReading(accountId, siteId, file, reading, source);
			return new Received(null, billId, reading);
		}
		Long supplierId = reading.read()
				? suppliers.bestMatch(accountId, reading.supplierName(), reading.supplierGstin()).map(Supplier::id).orElse(null)
				: null;
		boolean usable = reading.read() && !reading.lines().isEmpty()
				&& reading.confidence().doubleValue() >= READABLE_CONFIDENCE;
		String status = usable && member != null ? Delivery.AWAITING_COUNT : Delivery.NEEDS_REVIEW;
		long id = deliveries.create(new Deliveries.NewDelivery(accountId, siteId, supplierId,
				member == null ? null : member.id(), file.id(), source, caption, reading.read() ? Delivery.READ : Delivery.MANUAL,
				reading.note(), reading.supplierName(), reading.documentNumber(), reading.documentDate(),
				reading.vehicleNumber(), reading.confidence(), status, OffsetDateTime.now(clock)));
		List<Material> catalog = materials.active(accountId);
		int lineNo = 1;
		for (DocumentReader.Line line : reading.lines()) {
			Long materialId = MaterialMatcher.match(line.description(), catalog).map(Material::id).orElse(null);
			Unit unit = Unit.parse(line.unit());
			deliveries.addLine(id, lineNo++, materialId, line.description(), line.quantity(),
					unit != null ? unit.name() : line.unit(), null);
		}
		return new Received(deliveries.get(accountId, id), null, reading);
	}

	/** Applies a supervisor's "OK" or "1=195" to their latest slip awaiting a count. */
	@Transactional
	public List<Flag> applyCount(long accountId, long deliveryId, CountReply reply) {
		Delivery delivery = deliveries.get(accountId, deliveryId);
		for (DeliveryLine line : deliveries.lines(deliveryId)) {
			BigDecimal counted = reply.allAsSlip() || !reply.counts().containsKey(line.lineNo()) ? line.slipQty()
					: reply.counts().get(line.lineNo());
			deliveries.setReceived(line.id(), counted);
		}
		deliveries.setStatus(deliveryId, Delivery.CONFIRMED, OffsetDateTime.now(clock));
		return recordShortages(accountId, deliveries.get(accountId, delivery.id()));
	}

	/** The office's corrections: header, lines and counts. Confirms the delivery. */
	@Transactional
	public List<Flag> saveFromOffice(long accountId, long deliveryId, DeliveryForm form) {
		Delivery delivery = deliveries.get(accountId, deliveryId);
		long siteId = sites.get(accountId, form.getSiteId() != null ? form.getSiteId() : delivery.siteId()).id();
		Long supplierId = suppliers.find(accountId, form.getSupplierId()).map(Supplier::id).orElse(null);
		deliveries.updateHeader(accountId, deliveryId, siteId, supplierId, blank(form.getChallanNumber()),
				form.getChallanDate(), blank(form.getVehicleNumber()), blank(form.getNotes()));
		deliveries.deleteLines(deliveryId);
		int lineNo = 1;
		for (DeliveryForm.Line line : form.getLines()) {
			if (line.isBlank()) {
				continue;
			}
			Long materialId = materials.find(accountId, line.getMaterialId()).map(Material::id).orElse(null);
			String description = blank(line.getDescription()) != null ? line.getDescription().trim()
					: materials.find(accountId, materialId).map(Material::name).orElse("Material");
			BigDecimal received = line.getReceivedQty() != null ? line.getReceivedQty() : line.getSlipQty();
			deliveries.addLine(deliveryId, lineNo++, materialId, description, line.getSlipQty(), blank(line.getUnit()),
					received);
		}
		deliveries.setStatus(deliveryId, Delivery.CONFIRMED, OffsetDateTime.now(clock));
		return recordShortages(accountId, deliveries.get(accountId, deliveryId));
	}

	@Transactional
	public void reject(long accountId, long deliveryId) {
		deliveries.get(accountId, deliveryId);
		deliveries.setStatus(deliveryId, Delivery.REJECTED, null);
		flags.deleteOpenShortages(accountId, deliveryId);
	}

	/**
	 * Records a SHORT_DELIVERY finding for each line counted below the slip (beyond the material's
	 * tolerance), valued at the supplier's agreed rate when there is one.
	 */
	private List<Flag> recordShortages(long accountId, Delivery delivery) {
		flags.deleteOpenShortages(accountId, delivery.id());
		LocalDate date = delivery.challanDate() != null ? delivery.challanDate() : LocalDate.now(clock);
		List<RateAgreement> rates = delivery.supplierId() == null ? List.of()
				: suppliers.rates(accountId, delivery.supplierId());
		OffsetDateTime now = OffsetDateTime.now(clock);
		for (DeliveryLine line : deliveries.lines(delivery.id())) {
			Optional<Material> material = materials.find(accountId, line.materialId());
			if (material.isEmpty() || line.slipQty() == null || line.receivedQty() == null) {
				continue;
			}
			Material m = material.get();
			BigDecimal floor = line.slipQty().multiply(BigDecimal.ONE.subtract(m.tolerancePct().movePointLeft(2)));
			if (line.receivedQty().compareTo(floor) >= 0) {
				continue;
			}
			BigDecimal shortBy = line.shortBy();
			BigDecimal shortBase = Unit.convert(shortBy, line.parsedUnit(), m.baseUnit(), m);
			BigDecimal amount = shortBase == null ? BigDecimal.ZERO
					: Reconciler.agreedRatePerBase(rates, delivery.supplierId(), m, date)
						.map((rate) -> rate.multiply(shortBase).setScale(2, RoundingMode.HALF_UP))
						.orElse(BigDecimal.ZERO);
			String message = "Challan %s: slip says %s%s of %s, %s counted at the gate (%s%s short)%s.".formatted(
					delivery.challanNumber() == null ? "(no number)" : delivery.challanNumber(), qty(line.slipQty()),
					unitLabel(line.unit(), line.slipQty()), m.name(), qty(line.receivedQty()), qty(shortBy),
					unitLabel(line.unit(), shortBy),
					amount.signum() == 0 ? "; no agreed rate to value it" : "");
			flags.insert(accountId, delivery.siteId(), delivery.supplierId(), null, delivery.id(),
					new FlagDraft(Flag.SHORT_DELIVERY, m.id(), delivery.id(), message, line.slipQty(), line.receivedQty(),
							line.unit(), amount),
					now);
		}
		return flags.forDelivery(accountId, delivery.id())
			.stream()
			.filter((f) -> Flag.SHORT_DELIVERY.equals(f.kind()) && Flag.OPEN.equals(f.status()))
			.toList();
	}

	/** The message asking the supervisor to confirm what arrived. */
	public String askForCount(Delivery delivery, List<DeliveryLine> lines, String supplierName) {
		StringBuilder text = new StringBuilder("📦 Slip received");
		if (delivery.challanNumber() != null) {
			text.append(": challan ").append(delivery.challanNumber());
		}
		if (supplierName != null) {
			text.append(" · ").append(supplierName);
		}
		if (delivery.challanDate() != null) {
			text.append(" · ").append(DATE.format(delivery.challanDate()));
		}
		text.append('\n');
		for (DeliveryLine line : lines) {
			text.append(line.lineNo())
				.append(") ")
				.append(line.description())
				.append(" – ")
				.append(line.slipQty() == null ? "?" : qty(line.slipQty()))
				.append(unitLabel(line.unit(), line.slipQty()))
				.append('\n');
		}
		text.append("\nReply OK if everything arrived. If anything is short, reply with the line number and what you counted, e.g. ")
			.append(lines.isEmpty() ? "1=195" : lines.getFirst().lineNo() + "=" + example(lines.getFirst()));
		return text.toString();
	}

	/** The reply after a count is recorded. */
	public static String countRecorded(List<Flag> shortages) {
		if (shortages.isEmpty()) {
			return "✅ Recorded: everything arrived as per the slip. Thank you!";
		}
		StringBuilder text = new StringBuilder("✅ Recorded. Short at the gate:");
		BigDecimal total = BigDecimal.ZERO;
		for (Flag flag : shortages) {
			text.append("\n• ").append(flag.message().replaceFirst("^Challan [^:]+: ", ""));
			total = total.add(flag.amount());
		}
		if (total.signum() > 0) {
			text.append("\nValue: ").append(IndianMoney.rupees(total)).append(". The office will claim it from the supplier.");
		}
		return text.toString();
	}

	private static String example(DeliveryLine line) {
		if (line.slipQty() == null) {
			return "195";
		}
		BigDecimal less = line.slipQty().compareTo(BigDecimal.TEN) > 0 ? line.slipQty().subtract(BigDecimal.valueOf(5))
				: line.slipQty();
		return qty(less);
	}

	/** The unit after a quantity, with its leading space; empty when the line has no unit. */
	static String unitLabel(String unit, BigDecimal quantity) {
		if (unit == null) {
			return "";
		}
		Unit parsed = Unit.parse(unit);
		return " " + (parsed != null ? parsed.label(quantity) : unit.toLowerCase(Locale.ROOT));
	}

	static String qty(BigDecimal value) {
		BigDecimal rounded = value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
		return IndianMoney.group(rounded, Math.max(0, rounded.scale()));
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	/** The outcome of receiving a photo: a delivery, or a bill when the photo was a bill. */
	public record Received(Delivery delivery, Long billId, DocumentReader.Reading reading) {

		public boolean isBill() {
			return billId != null;
		}

	}

}
