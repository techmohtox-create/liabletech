package com.liabletech.payoutcheck.ingest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liabletech.payoutcheck.audit.AuditService;
import com.liabletech.payoutcheck.common.BadRequestException;
import com.liabletech.payoutcheck.common.Platform;
import com.liabletech.payoutcheck.ingest.ImportResult.RowError;
import com.liabletech.payoutcheck.orders.OrderRecord;
import com.liabletech.payoutcheck.orders.OrderRepository;
import com.liabletech.payoutcheck.outlet.Outlet;
import com.liabletech.payoutcheck.outlet.OutletService;
import com.liabletech.payoutcheck.recon.ReconciliationService;
import com.liabletech.payoutcheck.security.CurrentUser;

/**
 * Imports the restaurant's own order list. Re-importing an overlapping export is safe: orders are
 * keyed by outlet, platform and order ID and later files overwrite earlier ones.
 */
@Service
public class OrderImportService {

	static final int MAX_REPORTED_ERRORS = 100;

	private final OutletService outlets;

	private final OrderRepository orders;

	private final ImportBatchRepository batches;

	private final ReconciliationService reconciliation;

	private final AuditService audit;

	public OrderImportService(OutletService outlets, OrderRepository orders, ImportBatchRepository batches,
			ReconciliationService reconciliation, AuditService audit) {
		this.outlets = outlets;
		this.orders = orders;
		this.batches = batches;
		this.reconciliation = reconciliation;
		this.audit = audit;
	}

	@Transactional
	public ImportResult importOrders(CurrentUser actor, long outletId, Platform defaultPlatform, String fileName, byte[] content) {
		return importOrders(actor.orgId(), actor.id(), actor.email(), outletId, defaultPlatform, fileName, content);
	}

	@Transactional
	public ImportResult importOrders(long orgId, Long userId, String actorEmail, long outletId, Platform defaultPlatform,
			String fileName, byte[] content) {
		Outlet outlet = this.outlets.get(orgId, outletId);
		List<List<String>> rows = TabularReader.read(fileName, content);
		HeaderMatch header = HeaderMatch.find(rows, Formats.ORDERS);
		if (defaultPlatform == null && !header.has("platform")) {
			throw new BadRequestException("Choose the platform, or include a Platform column in the file");
		}
		Map<String, OrderRecord> parsed = new LinkedHashMap<>();
		List<RowError> errors = new ArrayList<>();
		int read = 0;
		int skipped = 0;
		int rejected = 0;
		for (int r = header.headerRow() + 1; r < rows.size(); r++) {
			List<String> row = rows.get(r);
			if (row.stream().allMatch(String::isBlank)) {
				continue;
			}
			read++;
			String ref = clean(header.value(row, "orderRef"));
			if (ref == null || ref.toLowerCase(Locale.ROOT).startsWith("total")) {
				skipped++;
				continue;
			}
			try {
				Platform platform = defaultPlatform;
				if (header.has("platform")) {
					String value = clean(header.value(row, "platform"));
					Platform fromRow = platformOf(value);
					if (fromRow == null && value != null) {
						// Dine-in, takeaway or another channel in a POS export.
						skipped++;
						continue;
					}
					if (fromRow != null) {
						platform = fromRow;
					}
				}
				if (platform == null) {
					throw new IllegalArgumentException("platform is missing");
				}
				OrderRecord order = new OrderRecord(0, outlet.id(), platform, ref, Values.dateTime(header.value(row, "orderedAt")),
						OrderStatus.parse(header.value(row, "status")), Values.money(header.value(row, "itemTotal")),
						Values.money(header.value(row, "packaging")), Values.deduction(header.value(row, "restaurantDiscount")));
				if (order.itemTotal().signum() < 0) {
					throw new IllegalArgumentException("item total is negative");
				}
				parsed.put(platform + ":" + ref, order);
			}
			catch (IllegalArgumentException ex) {
				rejected++;
				if (errors.size() < MAX_REPORTED_ERRORS) {
					errors.add(new RowError(r + 1, ex.getMessage()));
				}
			}
		}
		if (parsed.isEmpty()) {
			throw new BadRequestException(errors.isEmpty() ? "No orders found in the file"
					: "No orders could be read. First problem, row " + errors.get(0).row() + ": " + errors.get(0).message());
		}
		List<OrderRecord> records = new ArrayList<>(parsed.values());
		Platform batchPlatform = defaultPlatform != null ? defaultPlatform : records.get(0).platform();
		long importId = this.batches.insert(orgId, outlet.id(), "ORDERS", batchPlatform, fileName, read, records.size(), rejected,
				errors, userId);
		int created = this.orders.upsert(orgId, importId, records);

		int rechecked = 0;
		for (Platform platform : Platform.values()) {
			List<OrderRecord> forPlatform = records.stream().filter((o) -> o.platform() == platform).toList();
			if (forPlatform.isEmpty()) {
				continue;
			}
			LocalDate from = forPlatform.stream().map((o) -> o.orderedAt().toLocalDate()).min(LocalDate::compareTo).orElseThrow();
			LocalDate to = forPlatform.stream().map((o) -> o.orderedAt().toLocalDate()).max(LocalDate::compareTo).orElseThrow();
			rechecked += this.reconciliation.recheckAround(orgId, outlet.id(), platform, from, to, null);
		}
		this.audit.record(orgId, userId, actorEmail, "orders.import", "import_batch", importId,
				"%s: %d orders (%d new), %d rejected".formatted(fileName, records.size(), created, rejected));
		return new ImportResult(importId, "ORDERS", fileName, read, records.size(), created, skipped, rejected, errors,
				header.describe(), null, null, rechecked);
	}

	static Platform platformOf(String value) {
		if (value == null) {
			return null;
		}
		String text = value.toLowerCase(Locale.ROOT);
		if (text.contains("swiggy")) {
			return Platform.SWIGGY;
		}
		if (text.contains("zomato")) {
			return Platform.ZOMATO;
		}
		return null;
	}

	static String clean(String value) {
		if (value == null) {
			return null;
		}
		String text = value.trim();
		// Excel turns long numeric order IDs into "1.23456789E9" or "123456789.0".
		if (text.matches("\\d+\\.0+")) {
			text = text.substring(0, text.indexOf('.'));
		}
		return text.isEmpty() ? null : text;
	}

}
