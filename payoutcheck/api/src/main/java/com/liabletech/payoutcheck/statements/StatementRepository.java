package com.liabletech.payoutcheck.statements;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.payoutcheck.common.Platform;

@Repository
public class StatementRepository {

	private final JdbcClient jdbc;

	private final NamedParameterJdbcTemplate named;

	public StatementRepository(JdbcClient jdbc, NamedParameterJdbcTemplate named) {
		this.jdbc = jdbc;
		this.named = named;
	}

	public long insert(long orgId, long outletId, Platform platform, LocalDate periodStart, LocalDate periodEnd,
			LocalDate payoutDate, String utr, BigDecimal creditedAmount, String fileName, long importId, List<StatementLine> lines) {
		BigDecimal gross = lines.stream().map(StatementLine::netOrderValue).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal deductions = lines.stream()
			.map((l) -> l.totalDeductions().subtract(l.otherAdjustment().min(BigDecimal.ZERO)))
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal net = lines.stream().map(StatementLine::netPayout).reduce(BigDecimal.ZERO, BigDecimal::add);
		KeyHolder keys = new GeneratedKeyHolder();
		this.jdbc.sql("""
				INSERT INTO payout_statement (org_id, outlet_id, platform, period_start, period_end, payout_date, utr, credited_amount,
				    file_name, import_id, line_count, gross_sales, total_deductions, net_payout, created_at)
				VALUES (:orgId, :outletId, :platform, :start, :end, :payoutDate, :utr, :credited, :fileName, :importId, :lineCount,
				    :gross, :deductions, :net, :now)
				""")
			.param("orgId", orgId)
			.param("outletId", outletId)
			.param("platform", platform.name())
			.param("start", periodStart)
			.param("end", periodEnd)
			.param("payoutDate", payoutDate)
			.param("utr", utr)
			.param("credited", creditedAmount)
			.param("fileName", fileName)
			.param("importId", importId)
			.param("lineCount", lines.size())
			.param("gross", gross)
			.param("deductions", deductions)
			.param("net", net)
			.param("now", OffsetDateTime.now(ZoneOffset.UTC))
			.update(keys, "id");
		long id = keys.getKeyAs(Long.class);
		List<MapSqlParameterSource> batch = new ArrayList<>();
		for (StatementLine line : lines) {
			batch.add(new MapSqlParameterSource().addValue("statementId", id)
				.addValue("lineNo", line.lineNo())
				.addValue("orderRef", line.orderRef())
				.addValue("orderDate", line.orderDate())
				.addValue("orderStatus", line.orderStatus() == null ? null : line.orderStatus().name())
				.addValue("itemTotal", line.itemTotal())
				.addValue("packaging", line.packaging())
				.addValue("discount", line.restaurantDiscount())
				.addValue("commission", line.commission())
				.addValue("pgFee", line.pgFee())
				.addValue("otherFees", line.otherFees())
				.addValue("gst", line.gstOnFees())
				.addValue("tcs", line.tcs())
				.addValue("tds", line.tds())
				.addValue("refund", line.customerRefund())
				.addValue("cancellation", line.cancellationDeduction())
				.addValue("penalty", line.penalty())
				.addValue("adjustment", line.otherAdjustment())
				.addValue("net", line.netPayout())
				.addValue("description", line.description()));
		}
		this.named.batchUpdate("""
				INSERT INTO statement_line (statement_id, line_no, order_ref, order_date, order_status, item_total, packaging,
				    restaurant_discount, commission, pg_fee, other_fees, gst_on_fees, tcs, tds, customer_refund, cancellation_deduction,
				    penalty, other_adjustment, net_payout, description)
				VALUES (:statementId, :lineNo, :orderRef, :orderDate, :orderStatus, :itemTotal, :packaging, :discount, :commission,
				    :pgFee, :otherFees, :gst, :tcs, :tds, :refund, :cancellation, :penalty, :adjustment, :net, :description)
				""", batch.toArray(MapSqlParameterSource[]::new));
		return id;
	}

	public Optional<PayoutStatement> find(long orgId, long id) {
		return this.jdbc.sql("SELECT * FROM payout_statement WHERE id = :id AND org_id = :orgId")
			.param("id", id)
			.param("orgId", orgId)
			.query(PayoutStatement.class)
			.optional();
	}

	public boolean exists(long outletId, Platform platform, LocalDate start, LocalDate end) {
		return this.jdbc.sql("""
				SELECT COUNT(*) FROM payout_statement
				WHERE outlet_id = :outletId AND platform = :platform AND period_start = :start AND period_end = :end
				""")
			.param("outletId", outletId)
			.param("platform", platform.name())
			.param("start", start)
			.param("end", end)
			.query(Integer.class)
			.single() > 0;
	}

	public List<PayoutStatement> list(long orgId, Long outletId, Platform platform) {
		StringBuilder sql = new StringBuilder("SELECT * FROM payout_statement WHERE org_id = :orgId");
		MapSqlParameterSource params = new MapSqlParameterSource("orgId", orgId);
		if (outletId != null) {
			sql.append(" AND outlet_id = :outletId");
			params.addValue("outletId", outletId);
		}
		if (platform != null) {
			sql.append(" AND platform = :platform");
			params.addValue("platform", platform.name());
		}
		sql.append(" ORDER BY period_end DESC, id DESC");
		return this.jdbc.sql(sql.toString()).paramSource(params).query(PayoutStatement.class).list();
	}

	public List<PayoutStatement> forOutlet(long outletId, Platform platform) {
		return this.jdbc.sql("SELECT * FROM payout_statement WHERE outlet_id = :outletId AND platform = :platform ORDER BY period_start")
			.param("outletId", outletId)
			.param("platform", platform.name())
			.query(PayoutStatement.class)
			.list();
	}

	public List<StatementLine> lines(long statementId) {
		return this.jdbc.sql("SELECT * FROM statement_line WHERE statement_id = :id ORDER BY line_no")
			.param("id", statementId)
			.query(StatementLine.class)
			.list();
	}

	/** Rows for these orders in statements for periods that started before {@code before}. */
	public List<StatementLine> priorLines(long outletId, Platform platform, LocalDate before, Collection<String> refs) {
		List<StatementLine> lines = new ArrayList<>();
		List<String> all = new ArrayList<>(refs);
		for (int i = 0; i < all.size(); i += 500) {
			lines.addAll(this.jdbc.sql("""
					SELECT l.* FROM statement_line l JOIN payout_statement s ON s.id = l.statement_id
					WHERE s.outlet_id = :outletId AND s.platform = :platform AND s.period_start < :before AND l.order_ref IN (:refs)
					ORDER BY s.period_start, l.line_no
					""")
				.param("outletId", outletId)
				.param("platform", platform.name())
				.param("before", before)
				.param("refs", all.subList(i, Math.min(all.size(), i + 500)))
				.query(StatementLine.class)
				.list());
		}
		return lines;
	}

	/** Statements whose period overlaps the given dates. */
	public List<PayoutStatement> overlapping(long outletId, Platform platform, LocalDate from, LocalDate to) {
		return this.jdbc.sql("""
				SELECT * FROM payout_statement WHERE outlet_id = :outletId AND platform = :platform
				AND period_end >= :from AND period_start <= :to ORDER BY period_start
				""")
			.param("outletId", outletId)
			.param("platform", platform.name())
			.param("from", from)
			.param("to", to)
			.query(PayoutStatement.class)
			.list();
	}

	/** Order references paid in statements whose period overlaps the given dates. */
	public List<String> settledOrderRefs(long outletId, Platform platform, LocalDate from, LocalDate to) {
		return this.jdbc.sql("""
				SELECT DISTINCT l.order_ref FROM statement_line l JOIN payout_statement s ON s.id = l.statement_id
				WHERE s.outlet_id = :outletId AND s.platform = :platform AND l.order_ref IS NOT NULL
				AND s.period_end >= :from AND s.period_start <= :to
				""")
			.param("outletId", outletId)
			.param("platform", platform.name())
			.param("from", from)
			.param("to", to)
			.query(String.class)
			.list();
	}

	public void markChecked(long id) {
		this.jdbc.sql("UPDATE payout_statement SET checked_at = :now WHERE id = :id")
			.param("now", OffsetDateTime.now(ZoneOffset.UTC))
			.param("id", id)
			.update();
	}

	public void delete(long orgId, long id) {
		this.jdbc.sql("DELETE FROM finding WHERE statement_id = :id AND org_id = :orgId").param("id", id).param("orgId", orgId).update();
		this.jdbc.sql("DELETE FROM statement_line WHERE statement_id = :id").param("id", id).update();
		this.jdbc.sql("DELETE FROM payout_statement WHERE id = :id AND org_id = :orgId").param("id", id).param("orgId", orgId).update();
	}

}
