package com.liabletech.amc.customer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class Customers {

	private final JdbcClient jdbc;

	public Customers(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Customer get(long accountId, long id) {
		return find(accountId, id).orElseThrow(() -> new NotFoundException("Customer " + id));
	}

	public Optional<Customer> find(long accountId, Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM customer WHERE account_id = ? AND id = ?").params(accountId, id)
			.query(Customer.class)
			.optional();
	}

	public Optional<Customer> byPhone(long accountId, String phone) {
		if (phone == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM customer WHERE account_id = ? AND phone = ?")
			.params(accountId, phone)
			.query(Customer.class)
			.optional();
	}

	/**
	 * Businesses that have this phone as a customer, the one that messaged it last first: a reply on
	 * the shared number goes to whoever sent the reminder.
	 */
	public List<Long> accountsFor(String phone) {
		return jdbc.sql("""
				SELECT c.account_id FROM customer c
				LEFT JOIN (SELECT account_id, MAX(created_at) AS last_sent FROM outbound_message
				           WHERE recipient = :phone GROUP BY account_id) o ON o.account_id = c.account_id
				WHERE c.phone = :phone
				ORDER BY o.last_sent DESC NULLS LAST, c.id DESC
				""").param("phone", phone).query(Long.class).list();
	}

	/** The customer with this phone, created if new; a name fills a missing one. */
	public Customer findOrCreate(long accountId, String phone, String name, OffsetDateTime at) {
		Optional<Customer> existing = byPhone(accountId, phone);
		if (existing.isPresent()) {
			Customer customer = existing.get();
			if ((customer.name() == null || customer.name().isBlank()) && name != null && !name.isBlank()) {
				jdbc.sql("UPDATE customer SET name = ? WHERE id = ?").params(name.trim(), customer.id()).update();
				return get(accountId, customer.id());
			}
			return customer;
		}
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("INSERT INTO customer (account_id, name, phone, created_at) VALUES (?, ?, ?, ?)")
			.params(accountId, name == null || name.isBlank() ? null : name.trim(), phone, at)
			.update(keys, "id");
		return get(accountId, keys.getKeyAs(Long.class));
	}

	public void update(long accountId, long id, String name, String address, String area, String notes) {
		jdbc.sql("UPDATE customer SET name = ?, address = ?, area = ?, notes = ? WHERE account_id = ? AND id = ?")
			.params(blank(name), blank(address), blank(area), blank(notes), accountId, id)
			.update();
	}

	public void setOptedOut(long accountId, long id, boolean optedOut) {
		jdbc.sql("UPDATE customer SET opted_out = ? WHERE account_id = ? AND id = ?").params(optedOut, accountId, id).update();
	}

	public List<CustomerRow> list(long accountId, String search, int limit) {
		String like = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
		return jdbc.sql("""
				SELECT c.id, c.name, c.phone, c.area, c.opted_out,
				    (SELECT COUNT(*) FROM appliance a WHERE a.customer_id = c.id) AS appliances,
				    (SELECT COUNT(*) FROM contract k WHERE k.customer_id = c.id AND k.status = 'ACTIVE') AS active_contracts,
				    (SELECT MIN(k.ends_on) FROM contract k WHERE k.customer_id = c.id AND k.status = 'ACTIVE') AS next_end
				FROM customer c WHERE c.account_id = :accountId
				    AND (CAST(:search AS VARCHAR(200)) IS NULL OR LOWER(COALESCE(c.name, '')) LIKE :search
				         OR c.phone LIKE :search OR LOWER(COALESCE(c.area, '')) LIKE :search)
				ORDER BY c.name NULLS LAST, c.id LIMIT :limit
				""").param("accountId", accountId).param("search", like).param("limit", limit).query(CustomerRow.class).list();
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public record CustomerRow(Long id, String name, String phone, String area, boolean optedOut, long appliances,
			long activeContracts, LocalDate nextEnd) {
	}

}
