package com.liabletech.payoutcheck.ingest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liabletech.payoutcheck.audit.AuditService;
import com.liabletech.payoutcheck.common.BadRequestException;
import com.liabletech.payoutcheck.common.ConflictException;
import com.liabletech.payoutcheck.common.Platform;
import com.liabletech.payoutcheck.ingest.ImportResult.RowError;
import com.liabletech.payoutcheck.outlet.Outlet;
import com.liabletech.payoutcheck.outlet.OutletService;
import com.liabletech.payoutcheck.recon.ReconciliationService;
import com.liabletech.payoutcheck.recon.ReconciliationService.CheckSummary;
import com.liabletech.payoutcheck.security.CurrentUser;
import com.liabletech.payoutcheck.statements.StatementLine;
import com.liabletech.payoutcheck.statements.StatementRepository;

/** Imports one weekly payout annexure and checks it straight away. */
@Service
public class StatementImportService {

	private final OutletService outlets;

	private final StatementRepository statements;

	private final ImportBatchRepository batches;

	private final ReconciliationService reconciliation;

	private final AuditService audit;

	public StatementImportService(OutletService outlets, StatementRepository statements, ImportBatchRepository batches,
			ReconciliationService reconciliation, AuditService audit) {
		this.outlets = outlets;
		this.statements = statements;
		this.batches = batches;
		this.reconciliation = reconciliation;
		this.audit = audit;
	}

	/** What the owner types in next to the file: everything optional except the platform. */
	public record StatementDetails(Platform platform, LocalDate periodStart, LocalDate periodEnd, LocalDate payoutDate,
			String utr, BigDecimal creditedAmount) {
	}

	@Transactional
	public ImportResult importStatement(CurrentUser actor, long outletId, StatementDetails details, String fileName,
			byte[] content) {
		return importStatement(actor.orgId(), actor.id(), actor.email(), outletId, details, fileName, content);
	}

	@Transactional
	public ImportResult importStatement(long orgId, Long userId, String actorEmail, long outletId, StatementDetails details,
			String fileName, byte[] content) {
		Outlet outlet = this.outlets.get(orgId, outletId);
		Objects.requireNonNull(details.platform(), "platform");
		List<List<String>> rows = TabularReader.read(fileName, content);
		HeaderMatch header = HeaderMatch.find(rows, Formats.STATEMENT);
		if (!header.has("orderRef") && !header.has("description")) {
			throw new BadRequestException("Could not find an Order ID column in the statement");
		}
		List<StatementLine> lines = new ArrayList<>();
		List<RowError> errors = new ArrayList<>();
		int read = 0;
		int skipped = 0;
		for (int r = header.headerRow() + 1; r < rows.size(); r++) {
			List<String> row = rows.get(r);
			if (row.stream().allMatch(String::isBlank)) {
				continue;
			}
			read++;
			String ref = OrderImportService.clean(header.value(row, "orderRef"));
			String description = OrderImportService.clean(header.value(row, "description"));
			if (isTotalRow(ref, description, row)) {
				skipped++;
				continue;
			}
			try {
				String dateText = header.value(row, "orderDate");
				LocalDate orderDate = dateText == null || dateText.isBlank() ? null : Values.date(dateText);
				String statusText = header.value(row, "orderStatus");
				StatementLine line = new StatementLine(0, 0, lines.size() + 1, ref, orderDate,
						ref == null ? null : OrderStatus.parse(statusText), Values.money(header.value(row, "itemTotal")),
						Values.money(header.value(row, "packaging")), Values.deduction(header.value(row, "restaurantDiscount")),
						Values.deduction(header.value(row, "commission")), Values.deduction(header.value(row, "pgFee")),
						Values.deduction(header.value(row, "otherFees")), Values.deduction(header.value(row, "gstOnFees")),
						Values.deduction(header.value(row, "tcs")), Values.deduction(header.value(row, "tds")),
						Values.deduction(header.value(row, "customerRefund")),
						Values.deduction(header.value(row, "cancellationDeduction")), Values.deduction(header.value(row, "penalty")),
						Values.money(header.value(row, "otherAdjustment")), Values.money(header.value(row, "netPayout")),
						description == null ? null : description.substring(0, Math.min(description.length(), 255)));
				if (ref == null && line.netPayout().signum() == 0 && line.otherAdjustment().signum() == 0) {
					skipped++;
					continue;
				}
				lines.add(line);
			}
			catch (IllegalArgumentException ex) {
				if (errors.size() < OrderImportService.MAX_REPORTED_ERRORS) {
					errors.add(new RowError(r + 1, ex.getMessage()));
				}
			}
		}
		if (!errors.isEmpty()) {
			// A statement is checked as a whole; a partly read statement would produce wrong findings.
			throw new BadRequestException("The statement has %d row(s) that could not be read. First problem, row %d: %s"
				.formatted(errors.size(), errors.get(0).row(), errors.get(0).message()));
		}
		if (lines.isEmpty()) {
			throw new BadRequestException("No payout rows found in the file");
		}
		LocalDate start = details.periodStart() != null ? details.periodStart()
				: lines.stream().map(StatementLine::orderDate).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
		LocalDate end = details.periodEnd() != null ? details.periodEnd()
				: lines.stream().map(StatementLine::orderDate).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
		if (start == null || end == null) {
			throw new BadRequestException("Enter the statement period: the file has no order dates to work it out from");
		}
		if (end.isBefore(start)) {
			throw new BadRequestException("The period ends before it starts");
		}
		if (this.statements.exists(outlet.id(), details.platform(), start, end)) {
			throw new ConflictException("A %s statement for %s to %s is already uploaded for %s. Delete it first to replace it."
				.formatted(details.platform().label(), start, end, outlet.name()));
		}
		long importId = this.batches.insert(orgId, outlet.id(), "STATEMENT", details.platform(), fileName, read, lines.size(), 0,
				errors, userId);
		long statementId = this.statements.insert(orgId, outlet.id(), details.platform(), start, end, details.payoutDate(),
				blankToNull(details.utr()), details.creditedAmount(), fileName, importId, lines);
		CheckSummary check = this.reconciliation.check(orgId, statementId);
		int rechecked = this.reconciliation.recheckAround(orgId, outlet.id(), details.platform(), start, end, statementId);
		this.audit.record(orgId, userId, actorEmail, "statement.import", "payout_statement", statementId,
				"%s %s %s to %s: %d rows, %d findings".formatted(fileName, details.platform().label(), start, end, lines.size(),
						check.findings()));
		return new ImportResult(importId, "STATEMENT", fileName, read, lines.size(), lines.size(), skipped, 0, errors,
				header.describe(), statementId, check, rechecked);
	}

	private static boolean isTotalRow(String ref, String description, List<String> row) {
		if (ref != null && ref.toLowerCase(Locale.ROOT).startsWith("total")) {
			return true;
		}
		if (ref == null && description != null) {
			String text = description.toLowerCase(Locale.ROOT);
			return text.startsWith("total") || text.startsWith("grand total") || text.startsWith("net payout");
		}
		return ref == null && row.stream().anyMatch((cell) -> cell.trim().toLowerCase(Locale.ROOT).startsWith("total"));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
