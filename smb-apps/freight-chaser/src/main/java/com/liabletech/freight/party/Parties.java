package com.liabletech.freight.party;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.text.Phones;
import com.liabletech.platform.web.NotFoundException;

@Repository
public class Parties {

	private final JdbcClient jdbc;

	public Parties(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Party> all(long accountId) {
		return jdbc.sql("SELECT * FROM party WHERE account_id = ? ORDER BY name").param(accountId).query(Party.class).list();
	}

	public Party get(long accountId, long id) {
		return jdbc.sql("SELECT * FROM party WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Party.class)
			.optional()
			.orElseThrow(() -> new NotFoundException("Party " + id));
	}

	public long create(long accountId, PartyForm form) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO party (account_id, name, contact_person, phone, email, gstin, address, credit_days)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""")
			.params(accountId, form.getName().trim(), clean(form.getContactPerson()), phone(form.getPhone()),
					clean(form.getEmail()), upper(form.getGstin()), clean(form.getAddress()), form.getCreditDays())
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void update(long accountId, long id, PartyForm form) {
		jdbc.sql("""
				UPDATE party SET name = ?, contact_person = ?, phone = ?, email = ?, gstin = ?, address = ?,
				    credit_days = ?
				WHERE account_id = ? AND id = ?
				""")
			.params(form.getName().trim(), clean(form.getContactPerson()), phone(form.getPhone()), clean(form.getEmail()),
					upper(form.getGstin()), clean(form.getAddress()), form.getCreditDays(), accountId, id)
			.update();
	}

	/** Each party with what it owes and how fast it has paid over the past year. */
	public List<PartyBalance> balances(long accountId, LocalDate today) {
		List<Totals> totals = jdbc.sql("""
				SELECT p.id, p.name, p.phone, p.email, p.credit_days,
				    COALESCE(SUM(CASE WHEN i.status IN ('SENT', 'PART_PAID', 'DISPUTED')
				        THEN i.total - i.received - i.tds END), 0) AS outstanding,
				    COALESCE(SUM(CASE WHEN i.status IN ('SENT', 'PART_PAID', 'DISPUTED') AND i.due_date < :today
				        THEN i.total - i.received - i.tds END), 0) AS overdue,
				    COUNT(CASE WHEN i.status IN ('SENT', 'PART_PAID', 'DISPUTED') THEN 1 END) AS open_invoices
				FROM party p LEFT JOIN invoice i ON i.party_id = p.id
				WHERE p.account_id = :accountId
				GROUP BY p.id, p.name, p.phone, p.email, p.credit_days
				ORDER BY outstanding DESC, p.name
				""").param("accountId", accountId).param("today", today).query(Totals.class).list();
		// Days to pay are averaged here rather than in SQL: date arithmetic differs between H2 and PostgreSQL.
		Map<Long, Double> averages = jdbc.sql("""
				SELECT party_id, invoice_date, paid_on FROM invoice
				WHERE account_id = ? AND status = 'PAID' AND paid_on >= ?
				""").params(accountId, today.minusYears(1)).query(PaidInvoice.class).list().stream()
			.collect(Collectors.groupingBy(PaidInvoice::partyId,
					Collectors.averagingLong((paid) -> ChronoUnit.DAYS.between(paid.invoiceDate(), paid.paidOn()))));
		return totals.stream()
			.map((t) -> new PartyBalance(t.id(), t.name(), t.phone(), t.email(), t.creditDays(), t.outstanding(),
					t.overdue(), t.openInvoices(), averages.get(t.id())))
			.toList();
	}

	public record PartyBalance(Long id, String name, String phone, String email, int creditDays, BigDecimal outstanding,
			BigDecimal overdue, long openInvoices, Double avgDaysToPay) {

		/** True when the party pays later than the credit period it was given. */
		public boolean slowPayer() {
			return avgDaysToPay != null && avgDaysToPay > creditDays + 7;
		}

	}

	record Totals(Long id, String name, String phone, String email, int creditDays, BigDecimal outstanding,
			BigDecimal overdue, long openInvoices) {
	}

	record PaidInvoice(Long partyId, LocalDate invoiceDate, LocalDate paidOn) {
	}

	private static String clean(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String upper(String value) {
		String cleaned = clean(value);
		return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
	}

	private static String phone(String value) {
		String normalized = Phones.normalize(value);
		return normalized != null ? normalized : clean(value);
	}

}
