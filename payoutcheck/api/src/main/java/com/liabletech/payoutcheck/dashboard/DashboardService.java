package com.liabletech.payoutcheck.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.liabletech.payoutcheck.common.Money;
import com.liabletech.payoutcheck.common.Platform;
import com.liabletech.payoutcheck.recon.Finding;
import com.liabletech.payoutcheck.recon.FindingRepository;
import com.liabletech.payoutcheck.recon.FindingStatus;
import com.liabletech.payoutcheck.recon.FindingType;
import com.liabletech.payoutcheck.statements.PayoutStatement;
import com.liabletech.payoutcheck.statements.StatementRepository;

@Service
public class DashboardService {

	static final int WEEKS = 12;

	private final StatementRepository statements;

	private final FindingRepository findings;

	public DashboardService(StatementRepository statements, FindingRepository findings) {
		this.statements = statements;
		this.findings = findings;
	}

	public record Totals(BigDecimal grossSales, BigDecimal totalDeductions, BigDecimal netPayout, BigDecimal openAtRisk,
			BigDecimal disputedAtRisk, BigDecimal recovered, BigDecimal rejected, int statements, long openFindings,
			BigDecimal flaggedShareOfSales) {
	}

	public record Week(LocalDate periodEnd, BigDecimal grossSales, BigDecimal deductions, BigDecimal flagged,
			BigDecimal recovered) {
	}

	public record TypeBreakdown(FindingType type, String label, long count, BigDecimal amount) {
	}

	public record PlatformBreakdown(Platform platform, BigDecimal grossSales, BigDecimal deductions, BigDecimal openAtRisk,
			BigDecimal deductionRate) {
	}

	public record Dashboard(Totals totals, List<Week> weeks, List<TypeBreakdown> byType, List<PlatformBreakdown> byPlatform,
			List<Finding> topOpen) {
	}

	public Dashboard build(long orgId, Long outletId) {
		List<PayoutStatement> list = this.statements.list(orgId, outletId, null);
		List<Finding> all = this.findings.all(orgId, new FindingRepository.Filter(outletId, null, null, null, null, null, null));

		BigDecimal gross = sum(list.stream().map(PayoutStatement::grossSales).toList());
		BigDecimal deductions = sum(list.stream().map(PayoutStatement::totalDeductions).toList());
		BigDecimal net = sum(list.stream().map(PayoutStatement::netPayout).toList());
		BigDecimal open = atRisk(all, FindingStatus.OPEN);
		BigDecimal disputed = atRisk(all, FindingStatus.DISPUTED);
		BigDecimal recovered = sum(all.stream().map(Finding::recoveredAmount).toList());
		BigDecimal rejected = atRisk(all, FindingStatus.REJECTED);
		BigDecimal flagged = sum(all.stream()
			.filter((f) -> f.status() != FindingStatus.IGNORED)
			.map(Finding::amountAtRisk)
			.toList());
		BigDecimal share = gross.signum() > 0 ? flagged.movePointRight(2).divide(gross, 2, RoundingMode.HALF_UP)
				: Money.ZERO;
		long openCount = all.stream().filter((f) -> f.status() == FindingStatus.OPEN).count();
		Totals totals = new Totals(gross, deductions, net, open, disputed, recovered, rejected, list.size(), openCount, share);

		Map<Long, PayoutStatement> byId = new TreeMap<>();
		list.forEach((s) -> byId.put(s.id(), s));
		TreeMap<LocalDate, BigDecimal[]> weekly = new TreeMap<>();
		for (PayoutStatement s : list) {
			BigDecimal[] w = weekly.computeIfAbsent(s.periodEnd(), (k) -> zeros());
			w[0] = w[0].add(s.grossSales());
			w[1] = w[1].add(s.totalDeductions());
		}
		for (Finding f : all) {
			PayoutStatement s = byId.get(f.statementId());
			if (s == null || f.status() == FindingStatus.IGNORED) {
				continue;
			}
			BigDecimal[] w = weekly.computeIfAbsent(s.periodEnd(), (k) -> zeros());
			w[2] = w[2].add(f.amountAtRisk());
			w[3] = w[3].add(Money.of(f.recoveredAmount()));
		}
		List<Week> weeks = new ArrayList<>();
		weekly.descendingMap().entrySet().stream().limit(WEEKS).forEach((e) -> weeks.add(0,
				new Week(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3])));

		Map<FindingType, BigDecimal[]> types = new EnumMap<>(FindingType.class);
		for (Finding f : all) {
			if (f.status() != FindingStatus.OPEN && f.status() != FindingStatus.DISPUTED) {
				continue;
			}
			BigDecimal[] t = types.computeIfAbsent(f.type(), (k) -> zeros());
			t[0] = t[0].add(BigDecimal.ONE);
			t[1] = t[1].add(f.amountAtRisk());
		}
		List<TypeBreakdown> byType = types.entrySet()
			.stream()
			.map((e) -> new TypeBreakdown(e.getKey(), e.getKey().label(), e.getValue()[0].longValue(), e.getValue()[1]))
			.sorted(Comparator.comparing(TypeBreakdown::amount).reversed())
			.toList();

		List<PlatformBreakdown> byPlatform = new ArrayList<>();
		for (Platform platform : Platform.values()) {
			List<PayoutStatement> own = list.stream().filter((s) -> s.platform() == platform).toList();
			if (own.isEmpty()) {
				continue;
			}
			BigDecimal pGross = sum(own.stream().map(PayoutStatement::grossSales).toList());
			BigDecimal pDeductions = sum(own.stream().map(PayoutStatement::totalDeductions).toList());
			BigDecimal pOpen = sum(all.stream()
				.filter((f) -> f.platform() == platform)
				.filter((f) -> f.status() == FindingStatus.OPEN || f.status() == FindingStatus.DISPUTED)
				.map(Finding::amountAtRisk)
				.toList());
			BigDecimal rate = pGross.signum() > 0
					? pDeductions.movePointRight(2).divide(pGross, 2, RoundingMode.HALF_UP) : Money.ZERO;
			byPlatform.add(new PlatformBreakdown(platform, pGross, pDeductions, pOpen, rate));
		}

		List<Finding> topOpen = all.stream()
			.filter((f) -> f.status() == FindingStatus.OPEN)
			.sorted(Comparator.comparing(Finding::severity).thenComparing(Finding::amountAtRisk, Comparator.reverseOrder()))
			.limit(8)
			.toList();
		return new Dashboard(totals, weeks, byType, byPlatform, topOpen);
	}

	private static BigDecimal atRisk(List<Finding> findings, FindingStatus status) {
		return sum(findings.stream().filter((f) -> f.status() == status).map(Finding::amountAtRisk).toList());
	}

	private static BigDecimal sum(List<BigDecimal> values) {
		return values.stream().map(Money::of).reduce(Money.ZERO, BigDecimal::add);
	}

	private static BigDecimal[] zeros() {
		return new BigDecimal[] { Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO };
	}

}
