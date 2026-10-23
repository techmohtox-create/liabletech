package com.liabletech.materials.supplier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.materials.catalog.Unit;
import com.liabletech.platform.text.Fuzzy;
import com.liabletech.platform.web.NotFoundException;

@Repository
public class Suppliers {

	private final JdbcClient jdbc;

	public Suppliers(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Supplier> all(long accountId) {
		return jdbc.sql("SELECT * FROM supplier WHERE account_id = ? ORDER BY name")
			.param(accountId)
			.query(Supplier.class)
			.list();
	}

	public Supplier get(long accountId, long id) {
		return find(accountId, id).orElseThrow(() -> new NotFoundException("Supplier " + id));
	}

	public Optional<Supplier> find(long accountId, Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM supplier WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Supplier.class)
			.optional();
	}

	/** The supplier a name read off a slip most likely means, when the match is clear. */
	public Optional<Supplier> bestMatch(long accountId, String nameRead, String gstinRead) {
		List<Supplier> all = all(accountId);
		if (gstinRead != null && !gstinRead.isBlank()) {
			Optional<Supplier> byGstin = all.stream().filter((s) -> gstinRead.equalsIgnoreCase(s.gstin())).findFirst();
			if (byGstin.isPresent()) {
				return byGstin;
			}
		}
		if (nameRead == null || nameRead.isBlank()) {
			return Optional.empty();
		}
		return all.stream()
			.max(Comparator.comparingDouble((s) -> Fuzzy.similarity(s.name(), nameRead)))
			.filter((s) -> Fuzzy.similarity(s.name(), nameRead) >= 0.75);
	}

	public long create(long accountId, String name, String phone, String email, String gstin) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("INSERT INTO supplier (account_id, name, phone, email, gstin) VALUES (?, ?, ?, ?, ?)")
			.params(accountId, name.trim(), blank(phone), blank(email), gstin == null || gstin.isBlank() ? null : gstin.trim().toUpperCase())
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void update(long accountId, long id, String name, String phone, String email, String gstin) {
		jdbc.sql("UPDATE supplier SET name = ?, phone = ?, email = ?, gstin = ? WHERE account_id = ? AND id = ?")
			.params(name.trim(), blank(phone), blank(email), gstin == null || gstin.isBlank() ? null : gstin.trim().toUpperCase(),
					accountId, id)
			.update();
	}

	public List<RateAgreement> rates(long accountId, long supplierId) {
		return jdbc.sql("SELECT * FROM rate_agreement WHERE account_id = ? AND supplier_id = ? ORDER BY valid_from DESC")
			.params(accountId, supplierId)
			.query(RateAgreement.class)
			.list();
	}

	public List<RateAgreement> allRates(long accountId) {
		return jdbc.sql("SELECT * FROM rate_agreement WHERE account_id = ?")
			.param(accountId)
			.query(RateAgreement.class)
			.list();
	}

	public long addRate(long accountId, long supplierId, long materialId, BigDecimal rate, Unit unit, LocalDate from,
			LocalDate to) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO rate_agreement (account_id, supplier_id, material_id, rate, unit, valid_from, valid_to)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""").params(accountId, supplierId, materialId, rate, unit.name(), from, to).update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void deleteRate(long accountId, long rateId) {
		jdbc.sql("DELETE FROM rate_agreement WHERE account_id = ? AND id = ?").params(accountId, rateId).update();
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
