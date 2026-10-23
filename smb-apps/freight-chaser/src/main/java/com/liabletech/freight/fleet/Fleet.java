package com.liabletech.freight.fleet;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

/** Trucks and drivers of a fleet. */
@Repository
public class Fleet {

	private final JdbcClient jdbc;

	public Fleet(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Vehicle> vehicles(long accountId) {
		return jdbc.sql("SELECT * FROM vehicle WHERE account_id = ? ORDER BY active DESC, registration")
			.param(accountId)
			.query(Vehicle.class)
			.list();
	}

	public Optional<Vehicle> vehicle(long accountId, Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM vehicle WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Vehicle.class)
			.optional();
	}

	public Optional<Vehicle> vehicleByRegistration(long accountId, String registration) {
		return jdbc.sql("SELECT * FROM vehicle WHERE account_id = ? AND registration = ?")
			.params(accountId, Vehicle.normalize(registration))
			.query(Vehicle.class)
			.optional();
	}

	public long addVehicle(long accountId, String registration, String description) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("INSERT INTO vehicle (account_id, registration, description) VALUES (?, ?, ?)")
			.params(accountId, Vehicle.normalize(registration), blankToNull(description))
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void setVehicleActive(long accountId, long id, boolean active) {
		jdbc.sql("UPDATE vehicle SET active = ? WHERE account_id = ? AND id = ?").params(active, accountId, id).update();
	}

	public List<Driver> drivers(long accountId) {
		return jdbc.sql("SELECT * FROM driver WHERE account_id = ? ORDER BY active DESC, name")
			.param(accountId)
			.query(Driver.class)
			.list();
	}

	public Driver driver(long accountId, long id) {
		return jdbc.sql("SELECT * FROM driver WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Driver.class)
			.optional()
			.orElseThrow(() -> new NotFoundException("Driver " + id));
	}

	/** The driver with this WhatsApp number, in any fleet. */
	public Optional<Driver> driverByPhone(String phone) {
		return jdbc.sql("SELECT * FROM driver WHERE phone = ?").param(phone).query(Driver.class).optional();
	}

	public long addDriver(long accountId, String name, String phone, Long vehicleId) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("INSERT INTO driver (account_id, name, phone, vehicle_id) VALUES (?, ?, ?, ?)")
			.params(accountId, name.trim(), phone, vehicleId)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void updateDriver(long accountId, long id, String name, Long vehicleId, boolean active) {
		jdbc.sql("UPDATE driver SET name = ?, vehicle_id = ?, active = ? WHERE account_id = ? AND id = ?")
			.params(name.trim(), vehicleId, active, accountId, id)
			.update();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
