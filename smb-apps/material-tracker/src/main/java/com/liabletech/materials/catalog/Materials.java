package com.liabletech.materials.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class Materials {

	private final JdbcClient jdbc;

	public Materials(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Material> all(long accountId) {
		return jdbc.sql("SELECT * FROM material WHERE account_id = ? ORDER BY category, name")
			.param(accountId)
			.query(Material.class)
			.list();
	}

	public List<Material> active(long accountId) {
		return jdbc.sql("SELECT * FROM material WHERE account_id = ? AND active = TRUE ORDER BY category, name")
			.param(accountId)
			.query(Material.class)
			.list();
	}

	public Material get(long accountId, long id) {
		return find(accountId, id).orElseThrow(() -> new NotFoundException("Material " + id));
	}

	public Optional<Material> find(long accountId, Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM material WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Material.class)
			.optional();
	}

	public long create(long accountId, String name, String category, Unit unit, String aliases, BigDecimal bagKg,
			Integer barDiameterMm, BigDecimal tolerancePct) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO material (account_id, name, category, unit, aliases, bag_kg, bar_diameter_mm, tolerance_pct)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""")
			.params(accountId, name.trim(), category, unit.name(), aliases, bagKg, barDiameterMm, tolerancePct)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void update(long accountId, long id, String name, String aliases, BigDecimal tolerancePct, boolean active) {
		jdbc.sql("UPDATE material SET name = ?, aliases = ?, tolerance_pct = ?, active = ? WHERE account_id = ? AND id = ?")
			.params(name.trim(), aliases, tolerancePct, active, accountId, id)
			.update();
	}

}
