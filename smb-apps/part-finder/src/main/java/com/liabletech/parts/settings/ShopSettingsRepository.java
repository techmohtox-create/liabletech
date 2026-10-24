package com.liabletech.parts.settings;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.account.AccountCreated;

@Repository
public class ShopSettingsRepository {

	private final JdbcClient jdbc;

	public ShopSettingsRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public ShopSettings get(long accountId) {
		return jdbc.sql("SELECT * FROM shop_settings WHERE account_id = ?")
			.param(accountId)
			.query(ShopSettings.class)
			.optional()
			.orElse(ShopSettings.defaults(accountId));
	}

	public void save(ShopSettings settings) {
		int updated = jdbc.sql("""
				UPDATE shop_settings SET acknowledge = ?, auto_quote = ?, auto_quote_confidence = ?, nudge_minutes = ?,
				    ready_minutes = ?
				WHERE account_id = ?
				""").params(settings.acknowledge(), settings.autoQuote(), settings.autoQuoteConfidence(),
				settings.nudgeMinutes(), settings.readyMinutes(), settings.accountId()).update();
		if (updated == 0) {
			jdbc.sql("""
					INSERT INTO shop_settings (account_id, acknowledge, auto_quote, auto_quote_confidence, nudge_minutes,
					    ready_minutes)
					VALUES (?, ?, ?, ?, ?, ?)
					""").params(settings.accountId(), settings.acknowledge(), settings.autoQuote(),
					settings.autoQuoteConfidence(), settings.nudgeMinutes(), settings.readyMinutes()).update();
		}
	}

	@EventListener
	public void onAccountCreated(AccountCreated event) {
		save(ShopSettings.defaults(event.accountId()));
	}

}
