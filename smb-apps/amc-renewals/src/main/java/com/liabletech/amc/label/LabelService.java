package com.liabletech.amc.label;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.liabletech.amc.contract.Contract;
import com.liabletech.amc.contract.ContractService;
import com.liabletech.amc.contract.Contracts;
import com.liabletech.amc.customer.Appliance;
import com.liabletech.amc.customer.ApplianceKind;
import com.liabletech.amc.customer.Appliances;
import com.liabletech.amc.customer.Customer;
import com.liabletech.amc.customer.Customers;
import com.liabletech.amc.plan.AmcPlan;
import com.liabletech.amc.plan.Plans;
import com.liabletech.amc.team.Technician;
import com.liabletech.platform.ai.AiResult;
import com.liabletech.platform.ai.DocumentAi;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.text.Lenient;
import com.liabletech.platform.text.Phones;

/**
 * Turns a technician's label photo into an appliance on the customer's record, and the AMC sold with
 * it into a contract. A clear label with the customer's number in the caption needs no office work.
 */
@Service
public class LabelService {

	static final double AUTO_LINK_CONFIDENCE = 0.75;

	private final DocumentAi ai;

	private final LabelScans scans;

	private final Customers customers;

	private final Appliances appliances;

	private final Plans plans;

	private final ContractService contractService;

	private final Contracts contracts;

	private final Clock clock;

	public LabelService(DocumentAi ai, LabelScans scans, Customers customers, Appliances appliances, Plans plans,
			ContractService contractService, Contracts contracts, Clock clock) {
		this.ai = ai;
		this.scans = scans;
		this.customers = customers;
		this.appliances = appliances;
		this.plans = plans;
		this.contractService = contractService;
		this.contracts = contracts;
		this.clock = clock;
	}

	public Received receive(long accountId, Technician technician, StoredFile file, String caption) {
		CaptionParser.Caption parsed = CaptionParser.parse(caption);
		AiResult<LabelReading> result = ai.read(new DocumentAi.Request<>(accountId, "appliance-label",
				LabelReading.INSTRUCTIONS, List.of(file), caption, LabelReading.class));
		LabelReading reading = result.ok() ? result.value() : null;
		ApplianceKind kind = reading == null ? null : ApplianceKind.parse(reading.applianceType());
		if (kind == null && caption != null) {
			kind = guessKind(caption);
		}
		String capacity = reading == null ? null : Lenient.text(reading.capacity());
		long scanId = scans.create(new LabelScans.NewScan(accountId, technician == null ? null : technician.id(), file.id(),
				cut(caption, 500), result.ok() ? LabelScan.READ : LabelScan.MANUAL,
				result.ok() ? null : result.message(), kind == null ? null : kind.name(),
				reading == null ? null : Lenient.text(reading.brand()), reading == null ? null : Lenient.text(reading.model()),
				reading == null ? null : Lenient.text(reading.serialNumber()), capacity,
				reading == null ? null : Lenient.text(reading.refrigerant()),
				reading == null ? null : Lenient.text(reading.manufactured()), parsed.phone(), parsed.name(), parsed.amcSold(),
				parsed.amount(), reading == null ? null
						: BigDecimal.valueOf(Math.max(0, Math.min(1, reading.confidence()))).setScale(2, RoundingMode.HALF_UP),
				OffsetDateTime.now(clock)));
		LabelScan scan = scans.get(accountId, scanId);
		boolean clear = reading != null && reading.confidence() >= AUTO_LINK_CONFIDENCE && scan.brand() != null
				&& (scan.model() != null || scan.serialNumber() != null) && kind != null;
		if (clear && parsed.phone() != null) {
			Long planId = parsed.amcSold() ? suggestPlan(accountId, kind, parsed).map(AmcPlan::id).orElse(null) : null;
			if (!parsed.amcSold() || planId != null) {
				Linked linked = link(accountId, scanId, LabelForm.of(scan, parsed, planId));
				return new Received(scans.get(accountId, scanId), linked, parsed);
			}
		}
		return new Received(scan, null, parsed);
	}

	/**
	 * Saves the label as an appliance of the customer with that phone (created if new). The same
	 * serial number photographed again updates the existing appliance. A plan makes it a contract, or
	 * renews the appliance's open one. Saving a label a second time only corrects the appliance.
	 */
	@Transactional
	public Linked link(long accountId, long scanId, LabelForm form) {
		LabelScan scan = scans.get(accountId, scanId);
		OffsetDateTime now = OffsetDateTime.now(clock);
		Optional<Appliance> saved = appliances.find(accountId, scan.applianceId());
		Customer customer;
		if (saved.isPresent()) {
			customer = customers.get(accountId, saved.get().customerId());
		}
		else {
			String phone = Phones.normalize(form.getCustomerPhone());
			if (phone == null) {
				throw new IllegalArgumentException("Enter the customer's mobile number.");
			}
			customer = customers.findOrCreate(accountId, phone, form.getCustomerName(), now);
		}
		boolean named = StringUtils.hasText(customer.name());
		if ((!named && StringUtils.hasText(form.getCustomerName())) || StringUtils.hasText(form.getAddress())
				|| StringUtils.hasText(form.getArea())) {
			customers.update(accountId, customer.id(), named ? customer.name() : form.getCustomerName(),
					StringUtils.hasText(form.getAddress()) ? form.getAddress() : customer.address(),
					StringUtils.hasText(form.getArea()) ? form.getArea() : customer.area(), customer.notes());
		}
		ApplianceKind kind = ApplianceKind.parse(form.getKind());
		if (kind == null) {
			kind = ApplianceKind.OTHER;
		}
		Optional<Appliance> same = saved.isPresent() ? saved : appliances.sameSerial(customer.id(), form.getSerialNumber());
		long applianceId;
		if (same.isPresent()) {
			applianceId = same.get().id();
			appliances.update(accountId, applianceId, kind, form.getBrand(), form.getModel(), form.getSerialNumber(),
					form.getCapacity(), form.getLocation());
		}
		else {
			applianceId = appliances.create(accountId, customer.id(), kind, form.getBrand(), form.getModel(),
					form.getSerialNumber(), form.getCapacity(), form.getRefrigerant(), form.getManufactured(),
					form.getLocation(), scan.fileId(), now);
		}
		Long contractId = null;
		Optional<AmcPlan> plan = plans.find(accountId, form.getPlanId());
		if (plan.isPresent() && !LabelScan.LINKED.equals(scan.status())) {
			BigDecimal price = form.getPrice() != null && form.getPrice().signum() > 0 ? form.getPrice() : plan.get().price();
			Optional<Contract> open = contracts.openForAppliance(applianceId);
			if (open.isPresent()) {
				contractId = contractService.renew(accountId, open.get().id(), plan.get().id(), price, form.isPaid(),
						form.getPaymentMode());
			}
			else {
				contractId = contractService.sell(accountId, customer.id(), applianceId, plan.get(), price,
						form.getStartsOn(), "TECHNICIAN", form.isPaid() ? price : null,
						form.isPaid() ? form.getPaymentMode() : null);
			}
		}
		scans.link(scanId, applianceId);
		return new Linked(customer.id(), applianceId, contractId);
	}

	/** The plan a caption points to: its name ("comprehensive", "gold"), its price, or the cheapest for the appliance. */
	public Optional<AmcPlan> suggestPlan(long accountId, ApplianceKind kind, CaptionParser.Caption caption) {
		if (kind == null) {
			return Optional.empty();
		}
		List<AmcPlan> options = plans.forFamily(accountId, kind.family());
		if (options.isEmpty()) {
			return Optional.empty();
		}
		if (caption.planHint() != null) {
			Optional<AmcPlan> named = options.stream()
				.filter((p) -> p.name().toLowerCase(Locale.ROOT).contains(caption.planHint()))
				.findFirst();
			if (named.isPresent()) {
				return named;
			}
		}
		if (caption.amount() != null) {
			Optional<AmcPlan> priced = options.stream().filter((p) -> p.price().compareTo(caption.amount()) == 0).findFirst();
			if (priced.isPresent()) {
				return priced;
			}
		}
		return Optional.of(options.getFirst());
	}

	private static ApplianceKind guessKind(String caption) {
		ApplianceKind kind = ApplianceKind.parse(caption);
		return kind == ApplianceKind.OTHER ? null : kind;
	}

	private static String cut(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	public record Linked(long customerId, long applianceId, Long contractId) {
	}

	/** What a label photo became: linked straight away, or waiting for the office. */
	public record Received(LabelScan scan, Linked linked, CaptionParser.Caption caption) {

		public boolean linkedNow() {
			return linked != null;
		}

	}

}
