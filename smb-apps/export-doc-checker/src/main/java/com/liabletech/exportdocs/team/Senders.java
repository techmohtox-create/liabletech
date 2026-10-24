package com.liabletech.exportdocs.team;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class Senders {

	private final JdbcClient jdbc;

	public Senders(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<DocSender> all(long accountId) {
		return jdbc.sql("SELECT * FROM doc_sender WHERE account_id = ? ORDER BY active DESC, name")
			.param(accountId)
			.query(DocSender.class)
			.list();
	}

	public Optional<DocSender> byPhone(String phone) {
		if (phone == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM doc_sender WHERE phone = ?").param(phone).query(DocSender.class).optional();
	}

	public long add(long accountId, String name, String phone) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("INSERT INTO doc_sender (account_id, name, phone) VALUES (?, ?, ?)")
			.params(accountId, name.trim(), phone)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void setActive(long accountId, long id, boolean active) {
		jdbc.sql("UPDATE doc_sender SET active = ? WHERE account_id = ? AND id = ?").params(active, accountId, id).update();
	}

}
