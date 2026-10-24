package com.liabletech.exportdocs.check;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class Findings {

	private final JdbcClient jdbc;

	public Findings(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Finding get(long accountId, long id) {
		return jdbc.sql("SELECT * FROM finding WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Finding.class)
			.optional()
			.orElseThrow(() -> new NotFoundException("Finding " + id));
	}

	/** Open findings first, the most serious on top; then accepted and fixed ones. */
	public List<Finding> forShipment(long shipmentId) {
		return jdbc.sql("""
				SELECT * FROM finding WHERE shipment_id = ?
				ORDER BY CASE status WHEN 'OPEN' THEN 0 WHEN 'ACCEPTED' THEN 1 ELSE 2 END,
				    CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'WARNING' THEN 1 ELSE 2 END, id
				""").param(shipmentId).query(Finding.class).list();
	}

	/**
	 * Brings the stored findings in line with a new check. Findings that are still there keep their
	 * status (an accepted one stays accepted); ones no longer found are marked fixed; a fixed one that
	 * comes back is reopened.
	 */
	public void sync(long accountId, long shipmentId, List<FindingDraft> drafts, OffsetDateTime now) {
		Map<String, Finding> existing = new LinkedHashMap<>();
		for (Finding finding : forShipment(shipmentId)) {
			existing.putIfAbsent(finding.fingerprint(), finding);
		}
		Set<String> current = new HashSet<>();
		for (FindingDraft draft : drafts) {
			String fingerprint = cut(draft.fingerprint(), 200);
			if (!current.add(fingerprint)) {
				continue;
			}
			Finding old = existing.get(fingerprint);
			if (old == null) {
				jdbc.sql("""
						INSERT INTO finding (account_id, shipment_id, severity, code, fingerprint, title, detail, expected, found,
						    documents, rule_ref, fixer, fix, status, created_at)
						VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)
						""")
					.params(accountId, shipmentId, draft.severity().name(), draft.code(), fingerprint, cut(draft.title(), 200),
							cut(draft.detail(), 2000), cut(draft.expected(), 1000), cut(draft.found(), 1000),
							cut(draft.documentList(), 100), cut(draft.ruleRef(), 100), draft.fixer().name(), cut(draft.fix(), 500),
							now)
					.update();
			}
			else {
				boolean reopen = Finding.FIXED.equals(old.status());
				jdbc.sql("""
						UPDATE finding SET severity = ?, title = ?, detail = ?, expected = ?, found = ?, documents = ?,
						    rule_ref = ?, fixer = ?, fix = ?, status = ?, resolved_at = ?
						WHERE id = ?
						""")
					.params(draft.severity().name(), cut(draft.title(), 200), cut(draft.detail(), 2000),
							cut(draft.expected(), 1000), cut(draft.found(), 1000), cut(draft.documentList(), 100),
							cut(draft.ruleRef(), 100), draft.fixer().name(), cut(draft.fix(), 500),
							reopen ? Finding.OPEN : old.status(), reopen ? null : old.resolvedAt(), old.id())
					.update();
			}
		}
		for (Finding old : existing.values()) {
			if (Finding.OPEN.equals(old.status()) && !current.contains(old.fingerprint())) {
				jdbc.sql("UPDATE finding SET status = 'FIXED', resolved_at = ? WHERE id = ?").params(now, old.id()).update();
			}
		}
	}

	public void setStatus(long accountId, long id, String status, String note, OffsetDateTime at) {
		jdbc.sql("UPDATE finding SET status = ?, note = ?, resolved_at = ? WHERE account_id = ? AND id = ?")
			.params(status, note, Finding.OPEN.equals(status) ? null : at, accountId, id)
			.update();
	}

	public Counts openCounts(long shipmentId) {
		return jdbc.sql("""
				SELECT COUNT(CASE WHEN severity = 'CRITICAL' THEN 1 END) AS critical,
				    COUNT(CASE WHEN severity = 'WARNING' THEN 1 END) AS warning,
				    COUNT(CASE WHEN severity = 'INFO' THEN 1 END) AS info
				FROM finding WHERE shipment_id = ? AND status = 'OPEN'
				""").param(shipmentId).query(Counts.class).single();
	}

	/** Discrepancies fixed before the documents reached the bank, across the business. */
	public long fixedSince(long accountId, OffsetDateTime since) {
		return jdbc.sql("""
				SELECT COUNT(*) FROM finding
				WHERE account_id = ? AND status = 'FIXED' AND severity = 'CRITICAL' AND resolved_at >= ?
				""").params(accountId, since).query(Long.class).single();
	}

	private static String cut(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max - 1) + "…";
	}

	public record Counts(long critical, long warning, long info) {
	}

}
