package com.liabletech.payoutcheck.recon;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liabletech.payoutcheck.common.NotFoundException;
import com.liabletech.payoutcheck.common.Platform;
import com.liabletech.payoutcheck.orders.OrderRecord;
import com.liabletech.payoutcheck.orders.OrderRepository;
import com.liabletech.payoutcheck.outlet.OutletService;
import com.liabletech.payoutcheck.outlet.PlatformTerms;
import com.liabletech.payoutcheck.statements.PayoutStatement;
import com.liabletech.payoutcheck.statements.StatementLine;
import com.liabletech.payoutcheck.statements.StatementRepository;

/** Loads what the engine needs for a statement, runs it and stores the findings. */
@Service
public class ReconciliationService {

	private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

	/** How far either side of a statement period to look for the same order paid elsewhere. */
	static final int SETTLEMENT_WINDOW_DAYS = 60;

	private final StatementRepository statements;

	private final OrderRepository orders;

	private final FindingRepository findings;

	private final OutletService outlets;

	public ReconciliationService(StatementRepository statements, OrderRepository orders, FindingRepository findings,
			OutletService outlets) {
		this.statements = statements;
		this.orders = orders;
		this.findings = findings;
		this.outlets = outlets;
	}

	public record CheckSummary(long statementId, int findings, int newFindings, int matchedLines, int unmatchedLines,
			int ordersInPeriod) {
	}

	@Transactional
	public CheckSummary check(long orgId, long statementId) {
		PayoutStatement statement = this.statements.find(orgId, statementId)
			.orElseThrow(() -> new NotFoundException("Statement"));
		List<StatementLine> lines = this.statements.lines(statementId);
		Set<String> refs = new HashSet<>();
		lines.stream().filter(StatementLine::hasOrder).forEach((l) -> refs.add(l.orderRef()));
		PlatformTerms terms = this.outlets.termsFor(statement.outletId(), statement.platform());
		Map<String, OrderRecord> byRef = this.orders.findByRefs(statement.outletId(), statement.platform(), refs);
		List<OrderRecord> delivered = this.orders.deliveredBetween(statement.outletId(), statement.platform(),
				statement.periodStart(), statement.periodEnd());
		Set<String> settledElsewhere = new HashSet<>(this.statements.settledOrderRefs(statement.outletId(),
				statement.platform(), statement.periodStart().minusDays(SETTLEMENT_WINDOW_DAYS),
				statement.periodEnd().plusDays(SETTLEMENT_WINDOW_DAYS)));
		List<StatementLine> prior = this.statements.priorLines(statement.outletId(), statement.platform(),
				statement.periodStart(), refs);

		ReconciliationEngine.Result result = ReconciliationEngine
			.run(new ReconciliationEngine.Input(statement.periodStart(), statement.periodEnd(), statement.creditedAmount(), terms,
					lines, byRef, delivered, settledElsewhere, prior));

		Map<String, Finding> existing = new HashMap<>();
		for (Finding finding : this.findings.forStatement(statementId)) {
			existing.put(finding.fingerprint(), finding);
		}
		int created = 0;
		Set<String> seen = new HashSet<>();
		for (FindingDraft draft : result.findings()) {
			if (!seen.add(draft.fingerprint())) {
				continue;
			}
			Finding previous = existing.remove(draft.fingerprint());
			if (previous != null) {
				this.findings.refresh(previous.id(), draft);
			}
			else {
				this.findings.insert(orgId, statement.outletId(), statementId, statement.platform(), draft);
				created++;
			}
		}
		// Findings that no longer apply go away unless someone has already acted on them.
		for (Finding stale : existing.values()) {
			if (stale.status() == FindingStatus.OPEN) {
				this.findings.delete(stale.id());
			}
		}
		this.statements.markChecked(statementId);
		log.info("Checked statement {}: {} findings ({} new)", statementId, seen.size(), created);
		return new CheckSummary(statementId, seen.size(), created, result.matchedLines(), result.unmatchedLines(),
				result.ordersInPeriod());
	}

	/** Re-checks the statements that new orders or a new statement could change the outcome of. */
	@Transactional
	public int recheckAround(long orgId, long outletId, Platform platform, LocalDate from, LocalDate to, Long skipStatementId) {
		int count = 0;
		for (PayoutStatement statement : this.statements.overlapping(outletId, platform, from.minusDays(SETTLEMENT_WINDOW_DAYS),
				to.plusDays(SETTLEMENT_WINDOW_DAYS))) {
			if (skipStatementId != null && statement.id() == skipStatementId) {
				continue;
			}
			check(orgId, statement.id());
			count++;
		}
		return count;
	}

}
