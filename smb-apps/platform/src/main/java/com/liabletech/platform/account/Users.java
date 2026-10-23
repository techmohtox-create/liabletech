package com.liabletech.platform.account;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class Users {

	private final JdbcClient jdbc;

	public Users(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<AppUser> findByEmail(String email) {
		return jdbc.sql("SELECT * FROM app_user WHERE email = ?")
			.param(email.trim().toLowerCase(Locale.ROOT))
			.query(AppUser.class)
			.optional();
	}

	public boolean emailTaken(String email) {
		return findByEmail(email).isPresent();
	}

	public List<AppUser> forAccount(long accountId) {
		return jdbc.sql("SELECT * FROM app_user WHERE account_id = ? ORDER BY role DESC, name")
			.param(accountId)
			.query(AppUser.class)
			.list();
	}

	public long create(long accountId, String name, String email, String passwordHash, String role) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO app_user (account_id, name, email, password_hash, role)
				VALUES (:accountId, :name, :email, :passwordHash, :role)
				""")
			.param("accountId", accountId)
			.param("name", name.trim())
			.param("email", email.trim().toLowerCase(Locale.ROOT))
			.param("passwordHash", passwordHash)
			.param("role", role)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void setActive(long accountId, long userId, boolean active) {
		jdbc.sql("UPDATE app_user SET active = ? WHERE id = ? AND account_id = ? AND role <> 'OWNER'")
			.params(active, userId, accountId)
			.update();
	}

}
