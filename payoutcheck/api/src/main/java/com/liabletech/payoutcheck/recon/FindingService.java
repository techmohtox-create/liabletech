package com.liabletech.payoutcheck.recon;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liabletech.payoutcheck.account.AccountRepository;
import com.liabletech.payoutcheck.account.Plan;
import com.liabletech.payoutcheck.audit.AuditService;
import com.liabletech.payoutcheck.common.BadRequestException;
import com.liabletech.payoutcheck.common.Csv;
import com.liabletech.payoutcheck.common.Inr;
import com.liabletech.payoutcheck.common.Money;
import com.liabletech.payoutcheck.common.NotFoundException;
import com.liabletech.payoutcheck.common.PageResult;
import com.liabletech.payoutcheck.outlet.Outlet;
import com.liabletech.payoutcheck.outlet.OutletService;
import com.liabletech.payoutcheck.security.CurrentUser;
import com.liabletech.payoutcheck.statements.PayoutStatement;
import com.liabletech.payoutcheck.statements.StatementRepository;

/** The dispute workflow: moving findings through their statuses and writing them up for the platform. */
@Service
public class FindingService {

	private final FindingRepository findings;

	private final StatementRepository statements;

	private final OutletService outlets;

	private final AccountRepository accounts;

	private final AuditService audit;

	public FindingService(FindingRepository findings, StatementRepository statements, OutletService outlets,
			AccountRepository accounts, AuditService audit) {
		this.findings = findings;
		this.statements = statements;
		this.outlets = outlets;
		this.accounts = accounts;
		this.audit = audit;
	}

	public PageResult<Finding> search(long orgId, FindingRepository.Filter filter, int page, int size) {
		return this.findings.search(orgId, filter, page, size);
	}

	public record StatusChange(FindingStatus status, String ticketRef, BigDecimal recoveredAmount, String notes) {
	}

	@Transactional
	public Finding update(CurrentUser actor, long id, StatusChange change) {
		Finding finding = this.findings.find(actor.orgId(), id).orElseThrow(() -> new NotFoundException("Finding"));
		apply(actor, finding, change);
		return this.findings.find(actor.orgId(), id).orElseThrow();
	}

	@Transactional
	public int bulkUpdate(CurrentUser actor, List<Long> ids, StatusChange change) {
		if (change.recoveredAmount() != null) {
			throw new BadRequestException("Record recovered amounts one finding at a time");
		}
		List<Finding> found = this.findings.findAll(actor.orgId(), ids);
		for (Finding finding : found) {
			apply(actor, finding, new StatusChange(change.status(),
					change.ticketRef() != null ? change.ticketRef() : finding.ticketRef(), null,
					change.notes() != null ? change.notes() : finding.notes()));
		}
		return found.size();
	}

	private void apply(CurrentUser actor, Finding finding, StatusChange change) {
		// Only a recovery carries an amount; it defaults to the full amount at risk.
		BigDecimal recovered = null;
		if (change.status() == FindingStatus.RECOVERED) {
			recovered = change.recoveredAmount() != null ? change.recoveredAmount() : finding.amountAtRisk();
			if (recovered.signum() < 0) {
				throw new BadRequestException("Recovered amount cannot be negative");
			}
		}
		String ticket = trim(change.ticketRef(), 80);
		String notes = trim(change.notes(), 1000);
		this.findings.updateStatus(finding.id(), change.status(), ticket, recovered == null ? null : Money.of(recovered), notes);
		this.audit.record(actor, "finding." + change.status().name().toLowerCase(), "finding", finding.id(),
				"%s %s%s%s".formatted(finding.type().label(), finding.orderRef() == null ? "" : finding.orderRef(),
						ticket == null ? "" : ", ticket " + ticket,
						recovered == null ? "" : ", recovered " + Inr.format(recovered)));
	}

	/** A message to paste into the platform's partner support ticket, one section per kind of issue. */
	public record DisputeLetter(String subject, String body, BigDecimal totalClaimed, int findings) {
	}

	public DisputeLetter letter(CurrentUser actor, long statementId, List<Long> findingIds) {
		Plan plan = this.accounts.findOrganization(actor.orgId()).orElseThrow().plan();
		if (!plan.disputeLetters()) {
			throw new BadRequestException("Dispute letters are part of the Growth and Pro plans");
		}
		PayoutStatement statement = this.statements.find(actor.orgId(), statementId)
			.orElseThrow(() -> new NotFoundException("Statement"));
		Outlet outlet = this.outlets.get(actor.orgId(), statement.outletId());
		List<Finding> chosen = (findingIds == null || findingIds.isEmpty() ? this.findings.forStatement(statementId)
				: this.findings.findAll(actor.orgId(), findingIds))
			.stream()
			.filter((f) -> f.statementId() == statementId)
			.filter((f) -> f.status() == FindingStatus.OPEN || f.status() == FindingStatus.DISPUTED)
			.filter((f) -> f.amountAtRisk().signum() > 0)
			.toList();
		if (chosen.isEmpty()) {
			throw new BadRequestException("There are no open findings with money at stake on this statement");
		}
		Map<FindingType, List<Finding>> byType = new LinkedHashMap<>();
		for (FindingType type : FindingType.values()) {
			List<Finding> ofType = chosen.stream().filter((f) -> f.type() == type).toList();
			if (!ofType.isEmpty()) {
				byType.put(type, ofType);
			}
		}
		String partnerId = switch (statement.platform()) {
			case SWIGGY -> outlet.swiggyId();
			case ZOMATO -> outlet.zomatoId();
		};
		BigDecimal total = chosen.stream().map(Finding::amountAtRisk).reduce(Money.ZERO, BigDecimal::add);
		String subject = "Payout dispute: %s%s, settlement %s to %s, %s".formatted(outlet.name(),
				partnerId == null ? "" : " (ID " + partnerId + ")", statement.periodStart(), statement.periodEnd(),
				Inr.format(total));
		StringBuilder body = new StringBuilder();
		body.append("Hello ").append(statement.platform().label()).append(" Partner Support,\n\n");
		body.append("We have reconciled the payout for ").append(outlet.name());
		if (partnerId != null) {
			body.append(" (restaurant ID ").append(partnerId).append(')');
		}
		body.append(" for orders from ").append(statement.periodStart()).append(" to ").append(statement.periodEnd());
		if (statement.utr() != null) {
			body.append(", UTR ").append(statement.utr());
		}
		body.append(", against our order records and the commission terms in our agreement. ");
		body.append("We found the following discrepancies and request a review and credit of ")
			.append(Inr.format(total))
			.append(".\n");
		int section = 1;
		for (Map.Entry<FindingType, List<Finding>> entry : byType.entrySet()) {
			BigDecimal subtotal = entry.getValue().stream().map(Finding::amountAtRisk).reduce(Money.ZERO, BigDecimal::add);
			body.append("\n").append(section++).append(". ").append(entry.getKey().label()).append(" (")
				.append(Inr.format(subtotal)).append(")\n");
			body.append(entry.getKey().disputeText()).append('\n');
			for (Finding finding : entry.getValue()) {
				body.append("   - ");
				if (finding.orderRef() != null) {
					body.append("Order ").append(finding.orderRef()).append(": ");
				}
				body.append(finding.explanation()).append('\n');
			}
		}
		body.append("\nTotal claimed: ").append(Inr.format(total)).append("\n\n");
		body.append("Please confirm the credit, or share the calculation and evidence behind each deduction you uphold.\n\n");
		body.append("Regards,\n").append(actor.fullName()).append('\n').append(outlet.name());
		if (outlet.city() != null) {
			body.append(", ").append(outlet.city());
		}
		body.append('\n');
		this.audit.record(actor, "dispute.letter", "payout_statement", statementId,
				chosen.size() + " findings, " + Inr.format(total));
		return new DisputeLetter(subject, body.toString(), total, chosen.size());
	}

	public String exportCsv(long orgId, FindingRepository.Filter filter) {
		Map<Long, String> outletNames = new LinkedHashMap<>();
		this.outlets.list(orgId).forEach((o) -> outletNames.put(o.id(), o.name()));
		List<List<?>> rows = new ArrayList<>();
		for (Finding f : this.findings.all(orgId, filter)) {
			rows.add(List.of(f.id(), outletNames.getOrDefault(f.outletId(), ""), f.platform().label(), f.statementId(),
					nullToEmpty(f.orderRef()), f.type().label(), f.severity(), nullToEmpty(f.expectedAmount()),
					nullToEmpty(f.actualAmount()), f.amountAtRisk(), f.status(), nullToEmpty(f.ticketRef()),
					nullToEmpty(f.recoveredAmount()), f.explanation(), nullToEmpty(f.notes()), f.createdAt().toLocalDate()));
		}
		return Csv.write(List.of("id", "outlet", "platform", "statement_id", "order_id", "type", "severity", "expected",
				"charged", "amount_at_risk", "status", "ticket", "recovered", "explanation", "notes", "found_on"), rows);
	}

	private static Object nullToEmpty(Object value) {
		return value == null ? "" : value;
	}

	private static String trim(String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String text = value.trim();
		return text.length() > max ? text.substring(0, max) : text;
	}

}
