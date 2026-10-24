package com.liabletech.amc.team;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class Technicians {

	private final JdbcClient jdbc;

	public Technicians(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Technician> all(long accountId) {
		return jdbc.sql("SELECT * FROM technician WHERE account_id = ? ORDER BY active DESC, name")
			.param(accountId)
			.query(Technician.class)
			.list();
	}

	public Optional<Technician> find(long accountId, Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM technician WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Technician.class)
			.optional();
	}

	public Optional<Technician> byPhone(String phone) {
		if (phone == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM technician WHERE phone = ?").param(phone).query(Technician.class).optional();
	}

	public long add(long accountId, String name, String phone) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("INSERT INTO technician (account_id, name, phone) VALUES (?, ?, ?)")
			.params(accountId, name.trim(), phone)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void setActive(long accountId, long id, boolean active) {
		jdbc.sql("UPDATE technician SET active = ? WHERE account_id = ? AND id = ?").params(active, accountId, id).update();
	}

}
