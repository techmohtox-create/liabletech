package com.liabletech.amc.contract;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class Contracts {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final JdbcClient jdbc;

	public Contracts(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Contract get(long accountId, long id) {
		return find(accountId, id).orElseThrow(() -> new NotFoundException("Contract " + id));
	}

	public Optional<Contract> find(long accountId, Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM contract WHERE account_id = ? AND id = ?").params(accountId, id)
			.query(Contract.class)
			.optional();
	}

	/** The contract behind a renewal link. */
	public Optional<Contract> byToken(String token) {
		if (token == null || token.length() < 16) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM contract WHERE renewal_token = ?").param(token).query(Contract.class).optional();
	}

	public long create(long accountId, long customerId, long applianceId, Long planId, String planName, BigDecimal price,
			LocalDate startsOn, LocalDate endsOn, int visits, String source, OffsetDateTime at) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO contract (account_id, customer_id, appliance_id, plan_id, plan_name, price, starts_on, ends_on,
				    visits_included, status, renewal_token, source, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
				""")
			.params(accountId, customerId, applianceId, planId, planName, price, startsOn, endsOn, visits, newToken(), source,
					at)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public List<Contract> forCustomer(long customerId) {
		return jdbc.sql("SELECT * FROM contract WHERE customer_id = ? ORDER BY ends_on DESC, id DESC").param(customerId)
			.query(Contract.class)
			.list();
	}

	/** The customer's contract most worth renewing: open, ending soonest. */
	public Optional<Contract> renewableFor(long customerId) {
		return jdbc.sql("SELECT * FROM contract WHERE customer_id = ? AND status = 'ACTIVE' ORDER BY ends_on, id LIMIT 1")
			.param(customerId)
			.query(Contract.class)
			.optional();
	}

	/** The appliance's open contract, if it has one. */
	public Optional<Contract> openForAppliance(long applianceId) {
		return jdbc.sql("SELECT * FROM contract WHERE appliance_id = ? AND status = 'ACTIVE' ORDER BY ends_on DESC LIMIT 1")
			.param(applianceId)
			.query(Contract.class)
			.optional();
	}

	public void markRenewed(long id, long renewedById) {
		jdbc.sql("UPDATE contract SET status = 'RENEWED', renewed_by_id = ? WHERE id = ?").params(renewedById, id).update();
	}

	public void cancel(long accountId, long id) {
		jdbc.sql("UPDATE contract SET status = 'CANCELLED' WHERE account_id = ? AND id = ?").params(accountId, id).update();
	}

	public void recordPayment(long accountId, long id, BigDecimal amount, LocalDate on, String mode) {
		jdbc.sql("UPDATE contract SET paid_amount = ?, paid_on = ?, payment_mode = ? WHERE account_id = ? AND id = ?")
			.params(amount, on, mode, accountId, id)
			.update();
	}

	/** Open contracts whose renewal reminders could fall due: ending within the window around today. */
	public List<Contract> endingBetween(long accountId, LocalDate from, LocalDate to) {
		return jdbc.sql("""
				SELECT k.* FROM contract k JOIN customer c ON c.id = k.customer_id
				WHERE k.account_id = ? AND k.status = 'ACTIVE' AND k.ends_on BETWEEN ? AND ? AND c.opted_out = FALSE
				ORDER BY k.ends_on
				""").params(accountId, from, to).query(Contract.class).list();
	}

	/** Contracts in force today, for service-visit reminders. */
	public List<Contract> inForce(long accountId, LocalDate today) {
		return jdbc.sql("""
				SELECT k.* FROM contract k JOIN customer c ON c.id = k.customer_id
				WHERE k.account_id = ? AND k.status = 'ACTIVE' AND k.starts_on <= ? AND k.ends_on >= ? AND c.opted_out = FALSE
				""").params(accountId, today, today).query(Contract.class).list();
	}

	public List<ContractRow> list(long accountId, String filter, LocalDate today, String search, int limit) {
		String like = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
		return jdbc.sql("""
				SELECT k.id, k.customer_id, c.name AS customer_name, c.phone, c.opted_out, a.kind, a.brand, a.capacity,
				    a.location, k.plan_name, k.price, k.starts_on, k.ends_on, k.status, k.paid_amount, k.renewal_token,
				    (SELECT COUNT(*) FROM reminder r WHERE r.contract_id = k.id AND r.kind = 'RENEWAL') AS reminders_sent,
				    (SELECT MAX(r.sent_at) FROM reminder r WHERE r.contract_id = k.id AND r.kind = 'RENEWAL') AS last_reminder,
				    (SELECT COUNT(*) FROM service_request q WHERE q.contract_id = k.id AND q.kind = 'RENEWAL'
				        AND q.status IN ('NEW', 'SCHEDULED')) AS open_requests
				FROM contract k
				JOIN customer c ON c.id = k.customer_id
				JOIN appliance a ON a.id = k.appliance_id
				WHERE k.account_id = :accountId
				    AND (:filter = 'ALL'
				        OR (:filter = 'DUE' AND k.status = 'ACTIVE' AND k.ends_on BETWEEN :lapseFrom AND :dueTo)
				        OR (:filter = 'IN_FORCE' AND k.status = 'ACTIVE' AND k.ends_on >= :today)
				        OR (:filter = 'LAPSED' AND k.status = 'ACTIVE' AND k.ends_on < :today)
				        OR (:filter = 'RENEWED' AND k.status = 'RENEWED')
				        OR (:filter = 'CANCELLED' AND k.status = 'CANCELLED'))
				    AND (CAST(:search AS VARCHAR(200)) IS NULL OR LOWER(COALESCE(c.name, '')) LIKE :search OR c.phone LIKE :search)
				ORDER BY CASE WHEN :filter = 'RENEWED' THEN NULL ELSE k.ends_on END, k.ends_on DESC, k.id
				LIMIT :limit
				""")
			.param("accountId", accountId)
			.param("filter", filter)
			.param("today", today)
			.param("lapseFrom", today.minusDays(30))
			.param("dueTo", today.plusDays(30))
			.param("search", like)
			.param("limit", limit)
			.query(ContractRow.class)
			.list();
	}

	/**
	 * The business's recurring income and how well it is kept. Renewals count in the month they were
	 * made, which for an early renewal is before the new year starts.
	 */
	public Metrics metrics(long accountId, LocalDate today, ZoneId zone) {
		OffsetDateTime monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toOffsetDateTime();
		return jdbc.sql("""
				SELECT
				    COUNT(CASE WHEN status = 'ACTIVE' AND ends_on >= :today THEN 1 END) AS active_count,
				    COALESCE(SUM(CASE WHEN status = 'ACTIVE' AND ends_on >= :today THEN price END), 0) AS active_value,
				    COUNT(CASE WHEN status = 'ACTIVE' AND ends_on BETWEEN :today AND :dueTo THEN 1 END) AS due_count,
				    COALESCE(SUM(CASE WHEN status = 'ACTIVE' AND ends_on BETWEEN :today AND :dueTo THEN price END), 0) AS due_value,
				    COUNT(CASE WHEN status = 'ACTIVE' AND ends_on BETWEEN :lapsedFrom AND :yesterday THEN 1 END) AS lapsed_count,
				    COALESCE(SUM(CASE WHEN status = 'ACTIVE' AND ends_on BETWEEN :lapsedFrom AND :yesterday THEN price END), 0)
				        AS lapsed_value,
				    COUNT(CASE WHEN source = 'RENEWAL' AND created_at >= :monthStart THEN 1 END) AS renewed_this_month,
				    COALESCE(SUM(CASE WHEN source = 'RENEWAL' AND created_at >= :monthStart THEN price END), 0) AS renewed_value,
				    COUNT(CASE WHEN status = 'RENEWED' AND ends_on BETWEEN :lapsedFrom AND :dueTo THEN 1 END) AS recent_renewed,
				    COUNT(CASE WHEN status = 'ACTIVE' AND ends_on BETWEEN :lapsedFrom AND :yesterday THEN 1 END) AS recent_lapsed
				FROM contract WHERE account_id = :accountId
				""")
			.param("accountId", accountId)
			.param("today", today)
			.param("yesterday", today.minusDays(1))
			.param("dueTo", today.plusDays(30))
			.param("lapsedFrom", today.minusDays(90))
			.param("monthStart", monthStart)
			.query(Metrics.class)
			.single();
	}

	private static String newToken() {
		byte[] bytes = new byte[18];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public record ContractRow(Long id, Long customerId, String customerName, String phone, boolean optedOut, String kind,
			String brand, String capacity, String location, String planName, BigDecimal price, LocalDate startsOn,
			LocalDate endsOn, String status, BigDecimal paidAmount, String renewalToken, long remindersSent,
			OffsetDateTime lastReminder, long openRequests) {

		public String appliance() {
			return com.liabletech.amc.customer.ApplianceKind.parse(kind) == null ? kind
					: (brand == null ? "" : brand + " ") + (capacity == null ? "" : capacity + " ")
							+ com.liabletech.amc.customer.ApplianceKind.parse(kind).label();
		}

	}

	/**
	 * @param recentRenewed contracts that ended in the last 90 days (or end in the next 30) and were renewed
	 * @param recentLapsed contracts that ended in the last 90 days and were not
	 */
	public record Metrics(long activeCount, BigDecimal activeValue, long dueCount, BigDecimal dueValue, long lapsedCount,
			BigDecimal lapsedValue, long renewedThisMonth, BigDecimal renewedValue, long recentRenewed, long recentLapsed) {

		/** Share of recently ended contracts that were renewed, or null with nothing to compare. */
		public Integer renewalRate() {
			long total = recentRenewed + recentLapsed;
			return total == 0 ? null : (int) Math.round(100.0 * recentRenewed / total);
		}

	}

}
