package com.liabletech.payoutcheck.account;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {

	private final JdbcClient jdbc;

	public AccountRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public long insertOrganization(String name, Plan plan) {
		KeyHolder keys = new GeneratedKeyHolder();
		this.jdbc.sql("INSERT INTO organization (name, plan, created_at) VALUES (:name, :plan, :now)")
			.param("name", name)
			.param("plan", plan.name())
			.param("now", now())
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public Optional<Organization> findOrganization(long id) {
		return this.jdbc.sql("SELECT * FROM organization WHERE id = :id").param("id", id).query(Organization.class).optional();
	}

	public void updatePlan(long orgId, Plan plan) {
		this.jdbc.sql("UPDATE organization SET plan = :plan WHERE id = :id").param("plan", plan.name()).param("id", orgId).update();
	}

	public void renameOrganization(long orgId, String name) {
		this.jdbc.sql("UPDATE organization SET name = :name WHERE id = :id").param("name", name).param("id", orgId).update();
	}

	public long insertUser(long orgId, String email, String fullName, String passwordHash, Role role) {
		KeyHolder keys = new GeneratedKeyHolder();
		this.jdbc.sql("""
				INSERT INTO app_user (org_id, email, full_name, password_hash, role, active, created_at)
				VALUES (:orgId, :email, :fullName, :hash, :role, TRUE, :now)
				""")
			.param("orgId", orgId)
			.param("email", email)
			.param("fullName", fullName)
			.param("hash", passwordHash)
			.param("role", role.name())
			.param("now", now())
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public Optional<AppUser> findUserByEmail(String email) {
		return this.jdbc.sql("SELECT * FROM app_user WHERE email = :email").param("email", email).query(AppUser.class).optional();
	}

	public Optional<AppUser> findUser(long orgId, long id) {
		return this.jdbc.sql("SELECT * FROM app_user WHERE id = :id AND org_id = :orgId")
			.param("id", id)
			.param("orgId", orgId)
			.query(AppUser.class)
			.optional();
	}

	public List<AppUser> listUsers(long orgId) {
		return this.jdbc.sql("SELECT * FROM app_user WHERE org_id = :orgId ORDER BY created_at")
			.param("orgId", orgId)
			.query(AppUser.class)
			.list();
	}

	public int countActiveUsers(long orgId) {
		return this.jdbc.sql("SELECT COUNT(*) FROM app_user WHERE org_id = :orgId AND active = TRUE")
			.param("orgId", orgId)
			.query(Integer.class)
			.single();
	}

	public int countActiveOwners(long orgId) {
		return this.jdbc.sql("SELECT COUNT(*) FROM app_user WHERE org_id = :orgId AND active = TRUE AND role = 'OWNER'")
			.param("orgId", orgId)
			.query(Integer.class)
			.single();
	}

	public void updateUser(long orgId, long id, Role role, boolean active) {
		this.jdbc.sql("UPDATE app_user SET role = :role, active = :active WHERE id = :id AND org_id = :orgId")
			.param("role", role.name())
			.param("active", active)
			.param("id", id)
			.param("orgId", orgId)
			.update();
	}

	public void updatePassword(long id, String passwordHash) {
		this.jdbc.sql("UPDATE app_user SET password_hash = :hash WHERE id = :id").param("hash", passwordHash).param("id", id).update();
	}

	public void touchLogin(long id) {
		this.jdbc.sql("UPDATE app_user SET last_login_at = :now WHERE id = :id").param("now", now()).param("id", id).update();
	}

	static OffsetDateTime now() {
		return OffsetDateTime.now(ZoneOffset.UTC);
	}

}
