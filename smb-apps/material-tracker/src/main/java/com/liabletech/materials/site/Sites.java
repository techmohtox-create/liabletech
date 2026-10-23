package com.liabletech.materials.site;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class Sites {

	private final JdbcClient jdbc;

	public Sites(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Site> all(long accountId) {
		return jdbc.sql("SELECT * FROM site WHERE account_id = ? ORDER BY active DESC, name")
			.param(accountId)
			.query(Site.class)
			.list();
	}

	public Site get(long accountId, long id) {
		return find(accountId, id).orElseThrow(() -> new NotFoundException("Site " + id));
	}

	public Optional<Site> find(long accountId, Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM site WHERE account_id = ? AND id = ?").params(accountId, id).query(Site.class).optional();
	}

	public long create(long accountId, String name, String location) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("INSERT INTO site (account_id, name, location) VALUES (?, ?, ?)")
			.params(accountId, name.trim(), location == null || location.isBlank() ? null : location.trim())
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void setActive(long accountId, long id, boolean active) {
		jdbc.sql("UPDATE site SET active = ? WHERE account_id = ? AND id = ?").params(active, accountId, id).update();
	}

	public long countActive(long accountId) {
		return jdbc.sql("SELECT COUNT(*) FROM site WHERE account_id = ? AND active = TRUE")
			.param(accountId)
			.query(Long.class)
			.single();
	}

	public List<SiteMember> members(long accountId) {
		return jdbc.sql("SELECT * FROM site_member WHERE account_id = ? ORDER BY active DESC, name")
			.param(accountId)
			.query(SiteMember.class)
			.list();
	}

	public Optional<SiteMember> memberByPhone(String phone) {
		return jdbc.sql("SELECT * FROM site_member WHERE phone = ?").param(phone).query(SiteMember.class).optional();
	}

	public SiteMember member(long accountId, long id) {
		return jdbc.sql("SELECT * FROM site_member WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(SiteMember.class)
			.optional()
			.orElseThrow(() -> new NotFoundException("Member " + id));
	}

	public long addMember(long accountId, long siteId, String name, String phone, String role) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("INSERT INTO site_member (account_id, site_id, name, phone, role) VALUES (?, ?, ?, ?, ?)")
			.params(accountId, siteId, name.trim(), phone, role)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void updateMember(long accountId, long id, long siteId, boolean active) {
		jdbc.sql("UPDATE site_member SET site_id = ?, active = ? WHERE account_id = ? AND id = ?")
			.params(siteId, active, accountId, id)
			.update();
	}

}
