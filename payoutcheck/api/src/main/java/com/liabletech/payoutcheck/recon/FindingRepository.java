package com.liabletech.payoutcheck.recon;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.liabletech.payoutcheck.common.PageResult;
import com.liabletech.payoutcheck.common.Platform;

@Repository
public class FindingRepository {

	private static final String COLUMNS = """
			id, outlet_id, statement_id, line_id, platform, order_ref, type, severity, expected_amount, actual_amount,
			amount_at_risk, explanation, status, ticket_ref, recovered_amount, notes, fingerprint, created_at, updated_at,
			resolved_at""";

	private final JdbcClient jdbc;

	public FindingRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Finding> forStatement(long statementId) {
		return this.jdbc.sql("SELECT " + COLUMNS + " FROM finding WHERE statement_id = :id ORDER BY amount_at_risk DESC, id")
			.param("id", statementId)
			.query(Finding.class)
			.list();
	}

	public void insert(long orgId, long outletId, long statementId, Platform platform, FindingDraft draft) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.sql("""
				INSERT INTO finding (org_id, outlet_id, statement_id, line_id, platform, order_ref, type, severity, expected_amount,
				    actual_amount, amount_at_risk, explanation, status, fingerprint, created_at, updated_at)
				VALUES (:orgId, :outletId, :statementId, :lineId, :platform, :orderRef, :type, :severity, :expected, :actual,
				    :atRisk, :explanation, 'OPEN', :fingerprint, :now, :now)
				""")
			.param("orgId", orgId)
			.param("outletId", outletId)
			.param("statementId", statementId)
			.param("lineId", draft.lineId())
			.param("platform", platform.name())
			.param("orderRef", draft.orderRef())
			.param("type", draft.type().name())
			.param("severity", draft.severity().name())
			.param("expected", draft.expectedAmount())
			.param("actual", draft.actualAmount())
			.param("atRisk", draft.amountAtRisk())
			.param("explanation", draft.explanation())
			.param("fingerprint", draft.fingerprint())
			.param("now", now)
			.update();
	}

	/** Refreshes the numbers of a finding found again on a re-run, leaving its dispute status alone. */
	public void refresh(long id, FindingDraft draft) {
		this.jdbc.sql("""
				UPDATE finding SET line_id = :lineId, severity = :severity, expected_amount = :expected, actual_amount = :actual,
				    amount_at_risk = :atRisk, explanation = :explanation
				WHERE id = :id
				""")
			.param("lineId", draft.lineId())
			.param("severity", draft.severity().name())
			.param("expected", draft.expectedAmount())
			.param("actual", draft.actualAmount())
			.param("atRisk", draft.amountAtRisk())
			.param("explanation", draft.explanation())
			.param("id", id)
			.update();
	}

	public void delete(long id) {
		this.jdbc.sql("DELETE FROM finding WHERE id = :id").param("id", id).update();
	}

	public Optional<Finding> find(long orgId, long id) {
		return this.jdbc.sql("SELECT " + COLUMNS + " FROM finding WHERE id = :id AND org_id = :orgId")
			.param("id", id)
			.param("orgId", orgId)
			.query(Finding.class)
			.optional();
	}

	public List<Finding> findAll(long orgId, List<Long> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return this.jdbc.sql("SELECT " + COLUMNS + " FROM finding WHERE org_id = :orgId AND id IN (:ids) ORDER BY type, amount_at_risk DESC")
			.param("orgId", orgId)
			.param("ids", ids)
			.query(Finding.class)
			.list();
	}

	public void updateStatus(long id, FindingStatus status, String ticketRef, BigDecimal recovered, String notes) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.sql("""
				UPDATE finding SET status = :status, ticket_ref = :ticketRef, recovered_amount = :recovered, notes = :notes,
				    updated_at = :now, resolved_at = :resolvedAt
				WHERE id = :id
				""")
			.param("status", status.name())
			.param("ticketRef", ticketRef)
			.param("recovered", recovered)
			.param("notes", notes)
			.param("now", now)
			.param("resolvedAt", status.closed() ? now : null)
			.param("id", id)
			.update();
	}

	public record Filter(Long outletId, Long statementId, Platform platform, FindingStatus status, FindingType type,
			Severity severity, String query) {
	}

	public PageResult<Finding> search(long orgId, Filter filter, int page, int size) {
		MapSqlParameterSource params = new MapSqlParameterSource("orgId", orgId);
		String where = where(filter, params);
		params.addValue("limit", size).addValue("offset", page * size);
		List<Finding> items = this.jdbc.sql("SELECT " + COLUMNS + " FROM finding" + where
				+ " ORDER BY CASE status WHEN 'OPEN' THEN 0 WHEN 'DISPUTED' THEN 1 ELSE 2 END, amount_at_risk DESC, id DESC LIMIT :limit OFFSET :offset")
			.paramSource(params)
			.query(Finding.class)
			.list();
		long total = this.jdbc.sql("SELECT COUNT(*) FROM finding" + where).paramSource(params).query(Long.class).single();
		return new PageResult<>(items, total, page, size);
	}

	public List<Finding> all(long orgId, Filter filter) {
		MapSqlParameterSource params = new MapSqlParameterSource("orgId", orgId);
		return this.jdbc.sql("SELECT " + COLUMNS + " FROM finding" + where(filter, params) + " ORDER BY created_at DESC, id DESC")
			.paramSource(params)
			.query(Finding.class)
			.list();
	}

	private static String where(Filter filter, MapSqlParameterSource params) {
		StringBuilder where = new StringBuilder(" WHERE org_id = :orgId");
		if (filter.outletId() != null) {
			where.append(" AND outlet_id = :outletId");
			params.addValue("outletId", filter.outletId());
		}
		if (filter.statementId() != null) {
			where.append(" AND statement_id = :statementId");
			params.addValue("statementId", filter.statementId());
		}
		if (filter.platform() != null) {
			where.append(" AND platform = :platform");
			params.addValue("platform", filter.platform().name());
		}
		if (filter.status() != null) {
			where.append(" AND status = :status");
			params.addValue("status", filter.status().name());
		}
		if (filter.type() != null) {
			where.append(" AND type = :type");
			params.addValue("type", filter.type().name());
		}
		if (filter.severity() != null) {
			where.append(" AND severity = :severity");
			params.addValue("severity", filter.severity().name());
		}
		if (filter.query() != null && !filter.query().isBlank()) {
			where.append(" AND (LOWER(order_ref) LIKE :query OR LOWER(explanation) LIKE :query OR LOWER(ticket_ref) LIKE :query)");
			params.addValue("query", "%" + filter.query().trim().toLowerCase() + "%");
		}
		return where.toString();
	}

}
