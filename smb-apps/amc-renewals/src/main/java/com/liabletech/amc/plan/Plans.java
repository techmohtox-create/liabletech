package com.liabletech.amc.plan;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class Plans {

	private final JdbcClient jdbc;

	public Plans(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<AmcPlan> all(long accountId) {
		return jdbc.sql("SELECT * FROM amc_plan WHERE account_id = ? ORDER BY active DESC, family, price")
			.param(accountId)
			.query(AmcPlan.class)
			.list();
	}

	/** Active plans that can be sold for an appliance family (AC, RO...), cheapest first. */
	public List<AmcPlan> forFamily(long accountId, String family) {
		return all(accountId).stream().filter((p) -> p.active() && p.appliesTo(family)).toList();
	}

	public Optional<AmcPlan> find(long accountId, Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM amc_plan WHERE account_id = ? AND id = ?").params(accountId, id)
			.query(AmcPlan.class)
			.optional();
	}

	public long create(long accountId, String name, String family, BigDecimal price, int months, int visits,
			String covers) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("INSERT INTO amc_plan (account_id, name, family, price, months, visits, covers) VALUES (?, ?, ?, ?, ?, ?, ?)")
			.params(accountId, name.trim(), family, price, months, visits, covers == null || covers.isBlank() ? null : covers.trim())
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void update(long accountId, long id, String name, BigDecimal price, int months, int visits, String covers,
			boolean active) {
		jdbc.sql("""
				UPDATE amc_plan SET name = ?, price = ?, months = ?, visits = ?, covers = ?, active = ?
				WHERE account_id = ? AND id = ?
				""")
			.params(name.trim(), price, months, visits, covers == null || covers.isBlank() ? null : covers.trim(), active,
					accountId, id)
			.update();
	}

}
