package com.liabletech.payoutcheck.statements;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.liabletech.payoutcheck.audit.AuditService;
import com.liabletech.payoutcheck.common.Money;
import com.liabletech.payoutcheck.common.NotFoundException;
import com.liabletech.payoutcheck.common.Platform;
import com.liabletech.payoutcheck.recon.Finding;
import com.liabletech.payoutcheck.recon.FindingRepository;
import com.liabletech.payoutcheck.recon.FindingStatus;
import com.liabletech.payoutcheck.recon.ReconciliationService;
import com.liabletech.payoutcheck.recon.ReconciliationService.CheckSummary;
import com.liabletech.payoutcheck.security.CurrentUser;
import com.liabletech.payoutcheck.security.Roles;

@RestController
@RequestMapping("/api/statements")
public class StatementController {

	private final StatementRepository statements;

	private final FindingRepository findings;

	private final ReconciliationService reconciliation;

	private final AuditService audit;

	public StatementController(StatementRepository statements, FindingRepository findings, ReconciliationService reconciliation,
			AuditService audit) {
		this.statements = statements;
		this.findings = findings;
		this.reconciliation = reconciliation;
		this.audit = audit;
	}

	public record StatementSummary(PayoutStatement statement, int findings, BigDecimal openAtRisk, BigDecimal recovered) {
	}

	public record StatementDetail(PayoutStatement statement, List<StatementLine> lines, List<Finding> findings) {
	}

	@GetMapping
	public List<StatementSummary> list(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) Long outletId,
			@RequestParam(required = false) String platform) {
		List<PayoutStatement> list = this.statements.list(user.orgId(), outletId,
				platform == null || platform.isBlank() ? null : Platform.parse(platform));
		Map<Long, List<Finding>> byStatement = this.findings
			.all(user.orgId(), new FindingRepository.Filter(outletId, null, null, null, null, null, null))
			.stream()
			.collect(Collectors.groupingBy(Finding::statementId));
		return list.stream().map((s) -> {
			List<Finding> own = byStatement.getOrDefault(s.id(), List.of());
			BigDecimal open = own.stream()
				.filter((f) -> f.status() == FindingStatus.OPEN || f.status() == FindingStatus.DISPUTED)
				.map(Finding::amountAtRisk)
				.reduce(Money.ZERO, BigDecimal::add);
			BigDecimal recovered = own.stream()
				.map((f) -> Money.of(f.recoveredAmount()))
				.reduce(Money.ZERO, BigDecimal::add);
			return new StatementSummary(s, own.size(), open, recovered);
		}).toList();
	}

	@GetMapping("/{id}")
	public StatementDetail get(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
		PayoutStatement statement = this.statements.find(user.orgId(), id).orElseThrow(() -> new NotFoundException("Statement"));
		return new StatementDetail(statement, this.statements.lines(id), this.findings.forStatement(id));
	}

	@PostMapping("/{id}/check")
	@PreAuthorize(Roles.EDITOR)
	public CheckSummary recheck(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
		CheckSummary summary = this.reconciliation.check(user.orgId(), id);
		this.audit.record(user, "statement.check", "payout_statement", id, summary.findings() + " findings");
		return summary;
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(Roles.EDITOR)
	@Transactional
	public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
		PayoutStatement statement = this.statements.find(user.orgId(), id).orElseThrow(() -> new NotFoundException("Statement"));
		this.statements.delete(user.orgId(), id);
		this.reconciliation.recheckAround(user.orgId(), statement.outletId(), statement.platform(), statement.periodStart(),
				statement.periodEnd(), null);
		this.audit.record(user, "statement.delete", "payout_statement", id,
				"%s %s to %s".formatted(statement.platform().label(), statement.periodStart(), statement.periodEnd()));
	}

}
