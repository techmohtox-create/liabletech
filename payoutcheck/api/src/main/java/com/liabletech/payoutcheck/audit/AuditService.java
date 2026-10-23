package com.liabletech.payoutcheck.audit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.liabletech.payoutcheck.common.PageResult;
import com.liabletech.payoutcheck.security.CurrentUser;

/** Append-only record of who changed what. Written in the same transaction as the change. */
@Service
public class AuditService {

	private final JdbcClient jdbc;

	public AuditService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void record(CurrentUser actor, String action, String entity, Long entityId, String detail) {
		record(actor.orgId(), actor.id(), actor.email(), action, entity, entityId, detail);
	}

	public void record(long orgId, Long userId, String actorEmail, String action, String entity, Long entityId, String detail) {
		this.jdbc.sql("""
				INSERT INTO audit_event (org_id, user_id, actor_email, action, entity, entity_id, detail, created_at)
				VALUES (:orgId, :userId, :actor, :action, :entity, :entityId, :detail, :now)
				""")
			.param("orgId", orgId)
			.param("userId", userId)
			.param("actor", actorEmail)
			.param("action", action)
			.param("entity", entity)
			.param("entityId", entityId)
			.param("detail", detail == null || detail.length() <= 1000 ? detail : detail.substring(0, 1000))
			.param("now", OffsetDateTime.now(ZoneOffset.UTC))
			.update();
	}

	public PageResult<AuditEvent> list(long orgId, int page, int size) {
		List<AuditEvent> items = this.jdbc.sql("""
				SELECT id, user_id, actor_email, action, entity, entity_id, detail, created_at FROM audit_event
				WHERE org_id = :orgId ORDER BY id DESC LIMIT :limit OFFSET :offset
				""")
			.param("orgId", orgId)
			.param("limit", size)
			.param("offset", page * size)
			.query(AuditEvent.class)
			.list();
		long total = this.jdbc.sql("SELECT COUNT(*) FROM audit_event WHERE org_id = :orgId")
			.param("orgId", orgId)
			.query(Long.class)
			.single();
		return new PageResult<>(items, total, page, size);
	}

}
