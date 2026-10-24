package com.liabletech.amc.customer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.text.Fuzzy;
import com.liabletech.platform.web.NotFoundException;

@Repository
public class Appliances {

	private final JdbcClient jdbc;

	public Appliances(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Appliance get(long accountId, long id) {
		return find(accountId, id).orElseThrow(() -> new NotFoundException("Appliance " + id));
	}

	public Optional<Appliance> find(long accountId, Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM appliance WHERE account_id = ? AND id = ?").params(accountId, id)
			.query(Appliance.class)
			.optional();
	}

	public List<Appliance> forCustomer(long customerId) {
		return jdbc.sql("SELECT * FROM appliance WHERE customer_id = ? ORDER BY id").param(customerId)
			.query(Appliance.class)
			.list();
	}

	/** The same appliance photographed again: same customer and serial number, as written by anyone. */
	public Optional<Appliance> sameSerial(long customerId, String serial) {
		if (serial == null || serial.isBlank()) {
			return Optional.empty();
		}
		return forCustomer(customerId).stream()
			.filter((a) -> a.serialNumber() != null && Fuzzy.code(a.serialNumber()).equals(Fuzzy.code(serial)))
			.findFirst();
	}

	public long create(long accountId, long customerId, ApplianceKind kind, String brand, String model, String serial,
			String capacity, String refrigerant, String manufactured, String location, Long labelFileId, OffsetDateTime at) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO appliance (account_id, customer_id, kind, brand, model, serial_number, capacity, refrigerant,
				    manufactured, location, label_file_id, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
			.params(accountId, customerId, kind.name(), cut(brand, 60), cut(model, 80), cut(serial, 80), cut(capacity, 40),
					cut(refrigerant, 20), cut(manufactured, 20), cut(location, 60), labelFileId, at)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void update(long accountId, long id, ApplianceKind kind, String brand, String model, String serial,
			String capacity, String location) {
		jdbc.sql("""
				UPDATE appliance SET kind = ?, brand = ?, model = ?, serial_number = ?, capacity = ?, location = ?
				WHERE account_id = ? AND id = ?
				""")
			.params(kind.name(), cut(brand, 60), cut(model, 80), cut(serial, 80), cut(capacity, 40), cut(location, 60),
					accountId, id)
			.update();
	}

	static String cut(String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}

}
