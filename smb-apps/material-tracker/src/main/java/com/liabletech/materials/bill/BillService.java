package com.liabletech.materials.bill;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.liabletech.materials.catalog.Material;
import com.liabletech.materials.catalog.MaterialMatcher;
import com.liabletech.materials.catalog.Materials;
import com.liabletech.materials.catalog.Unit;
import com.liabletech.materials.leakage.Flag;
import com.liabletech.materials.leakage.Flags;
import com.liabletech.materials.reading.DocumentReader;
import com.liabletech.materials.site.Site;
import com.liabletech.materials.site.Sites;
import com.liabletech.materials.supplier.Supplier;
import com.liabletech.materials.supplier.Suppliers;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.messaging.Messenger;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.messaging.Outgoing;
import com.liabletech.platform.text.IndianMoney;

/** Supplier bills: read, corrected, checked against deliveries, and disputed or approved. */
@Service
public class BillService {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private final Bills bills;

	private final DocumentReader reader;

	private final Materials materials;

	private final Suppliers suppliers;

	private final Sites sites;

	private final Flags flags;

	private final Accounts accounts;

	private final Messenger messenger;

	private final FileStore files;

	private final Clock clock;

	public BillService(Bills bills, DocumentReader reader, Materials materials, Suppliers suppliers, Sites sites,
			Flags flags, Accounts accounts, Messenger messenger, FileStore files, Clock clock) {
		this.bills = bills;
		this.reader = reader;
		this.materials = materials;
		this.suppliers = suppliers;
		this.sites = sites;
		this.flags = flags;
		this.accounts = accounts;
		this.messenger = messenger;
		this.files = files;
		this.clock = clock;
	}

	/** A bill photo or PDF uploaded by the office. */
	public long upload(long accountId, Long siteId, StoredFile file) {
		return createFromReading(accountId, siteId, file, reader.read(accountId, file, null), "UPLOAD");
	}

	public long createFromReading(long accountId, Long siteId, StoredFile file, DocumentReader.Reading reading,
			String source) {
		Long site = sites.find(accountId, siteId).map(Site::id).orElse(null);
		Long supplierId = reading.read()
				? suppliers.bestMatch(accountId, reading.supplierName(), reading.supplierGstin()).map(Supplier::id).orElse(null)
				: null;
		long billId = bills.create(new Bills.NewBill(accountId, site, supplierId, file.id(), source,
				reading.read() ? "READ" : "MANUAL", reading.note(), reading.supplierName(), reading.documentNumber(),
				reading.documentDate(),
				reading.challanReferences().isEmpty() ? null : String.join(", ", reading.challanReferences()),
				reading.taxableAmount(),
				reading.taxAmount(), reading.totalAmount(), reading.confidence(), OffsetDateTime.now(clock)));
		List<Material> catalog = materials.active(accountId);
		int lineNo = 1;
		for (DocumentReader.Line line : reading.lines()) {
			Long materialId = MaterialMatcher.match(line.description(), catalog).map(Material::id).orElse(null);
			Unit unit = Unit.parse(line.unit());
			bills.addLine(billId, lineNo++, materialId, line.description(), line.quantity(),
					unit != null ? unit.name() : line.unit(), line.rate(), line.amount(), line.challanReference());
		}
		return billId;
	}

	@Transactional
	public void save(long accountId, long billId, BillForm form) {
		bills.get(accountId, billId);
		Long siteId = sites.find(accountId, form.getSiteId()).map(Site::id).orElse(null);
		Long supplierId = suppliers.find(accountId, form.getSupplierId()).map(Supplier::id).orElse(null);
		bills.updateHeader(accountId, billId, siteId, supplierId, blank(form.getBillNumber()), form.getBillDate(),
				blank(form.getChallanRefs()), form.getTaxableAmount(), form.getTaxAmount(), form.getTotalAmount());
		bills.deleteLines(billId);
		int lineNo = 1;
		for (BillForm.Line line : form.getLines()) {
			if (line.isBlank()) {
				continue;
			}
			Long materialId = materials.find(accountId, line.getMaterialId()).map(Material::id).orElse(null);
			String description = blank(line.getDescription()) != null ? line.getDescription().trim()
					: materials.find(accountId, materialId).map(Material::name).orElse("Material");
			bills.addLine(billId, lineNo++, materialId, description, line.getQty(), blank(line.getUnit()), line.getRate(),
					line.getAmount(), blank(line.getChallanRef()));
		}
	}

	@Transactional
	public void setStatus(long accountId, long billId, String status) {
		bills.get(accountId, billId);
		bills.setStatus(accountId, billId, status);
		if (Bill.REJECTED.equals(status)) {
			for (Flag flag : flags.forBill(accountId, billId)) {
				if (flag.atRisk()) {
					flags.setStatus(accountId, flag.id(), Flag.DISMISSED, "Bill rejected", OffsetDateTime.now(clock));
				}
			}
		}
	}

	/**
	 * Sends the supplier a discrepancy note listing every open finding on the bill, with the bill
	 * attached, and marks the findings raised.
	 */
	@Transactional
	public List<OutboundMessage> raiseWithSupplier(long accountId, long billId) {
		Bill bill = bills.get(accountId, billId);
		if (bill.supplierId() == null) {
			throw new IllegalStateException("Choose the supplier first.");
		}
		Supplier supplier = suppliers.get(accountId, bill.supplierId());
		Account account = accounts.get(accountId);
		List<Flag> open = flags.forBill(accountId, billId).stream().filter((f) -> Flag.OPEN.equals(f.status())).toList();
		if (open.isEmpty()) {
			throw new IllegalStateException("There is nothing open to raise on this bill.");
		}
		String site = sites.find(accountId, bill.siteId()).map(Site::name).orElse(null);
		String text = discrepancyNote(account, supplier, bill, site, open);
		String subject = "Discrepancy in bill " + (bill.billNumber() == null ? "" : bill.billNumber()) + " – " + account.name();
		List<OutboundMessage> sent = new ArrayList<>();
		if (StringUtils.hasText(supplier.phone())) {
			Outgoing message = Outgoing.whatsApp(accountId, supplier.phone(), text).about("bill", billId);
			if (bill.fileId() != null) {
				message = message.attach(bill.fileId(), files.get(bill.fileId()).downloadName());
			}
			sent.add(messenger.send(message.template("material_bill_discrepancy", supplier.name(),
					bill.billNumber() == null ? "-" : bill.billNumber(), IndianMoney.rupees(total(open)), account.name())));
		}
		if (StringUtils.hasText(supplier.email())) {
			Outgoing message = Outgoing.email(accountId, supplier.email(), subject, text).about("bill", billId);
			if (bill.fileId() != null) {
				message = message.attach(bill.fileId(), files.get(bill.fileId()).downloadName());
			}
			sent.add(messenger.send(message));
		}
		if (sent.isEmpty()) {
			throw new IllegalStateException("Add the supplier's WhatsApp number or email first.");
		}
		OffsetDateTime now = OffsetDateTime.now(clock);
		for (Flag flag : open) {
			flags.setStatus(accountId, flag.id(), Flag.RAISED, "Sent to the supplier", now);
		}
		bills.setStatus(accountId, billId, Bill.DISPUTED);
		return sent;
	}

	/** The supplier sent a credit note or a corrected bill: the raised amounts are recovered. */
	@Transactional
	public void markRecovered(long accountId, long billId, String note) {
		bills.get(accountId, billId);
		OffsetDateTime now = OffsetDateTime.now(clock);
		for (Flag flag : flags.forBill(accountId, billId)) {
			if (flag.atRisk()) {
				flags.setStatus(accountId, flag.id(), Flag.RECOVERED,
						StringUtils.hasText(note) ? note.trim() : "Credit note received", now);
			}
		}
		bills.setStatus(accountId, billId, Bill.APPROVED);
	}

	static String discrepancyNote(Account account, Supplier supplier, Bill bill, String site, List<Flag> open) {
		StringBuilder text = new StringBuilder();
		text.append("Dear ").append(supplier.name()).append(",\n\n");
		text.append("We checked your bill");
		if (bill.billNumber() != null) {
			text.append(" no. ").append(bill.billNumber());
		}
		if (bill.billDate() != null) {
			text.append(" dated ").append(DATE.format(bill.billDate()));
		}
		if (site != null) {
			text.append(" for ").append(site);
		}
		text.append(" against the deliveries counted at site and our agreed rates, and found:\n");
		int n = 1;
		for (Flag flag : open) {
			text.append('\n').append(n++).append(". ").append(flag.title()).append(": ").append(flag.message());
			if (flag.amount().signum() > 0) {
				text.append(" (").append(IndianMoney.rupees(flag.amount())).append(")");
			}
		}
		BigDecimal total = total(open);
		if (total.signum() > 0) {
			text.append("\n\nTotal to be corrected: ").append(IndianMoney.rupees(total)).append('.');
		}
		text.append("\nPlease send a credit note or a revised bill. Payment of the undisputed amount will follow.\n\nRegards,\n")
			.append(account.name());
		if (StringUtils.hasText(account.phone())) {
			text.append('\n').append(account.phone());
		}
		return text.toString();
	}

	private static BigDecimal total(List<Flag> flags) {
		return flags.stream().map(Flag::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
