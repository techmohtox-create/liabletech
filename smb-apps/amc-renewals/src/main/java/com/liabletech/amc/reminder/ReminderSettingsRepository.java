package com.liabletech.amc.reminder;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReminderSettingsRepository {

	private final JdbcClient jdbc;

	public ReminderSettingsRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public ReminderSettings get(long accountId) {
		return jdbc.sql("SELECT * FROM reminder_settings WHERE account_id = ?")
			.param(accountId)
			.query(ReminderSettings.class)
			.optional()
			.orElse(ReminderSettings.defaults(accountId));
	}

	public void save(ReminderSettings settings) {
		int updated = jdbc.sql("""
				UPDATE reminder_settings SET renewal_offsets = ?, service_reminders = ?, auto_send = ? WHERE account_id = ?
				""").params(settings.renewalOffsets(), settings.serviceReminders(), settings.autoSend(), settings.accountId())
			.update();
		if (updated == 0) {
			jdbc.sql("INSERT INTO reminder_settings (account_id, renewal_offsets, service_reminders, auto_send) VALUES (?, ?, ?, ?)")
				.params(settings.accountId(), settings.renewalOffsets(), settings.serviceReminders(), settings.autoSend())
				.update();
		}
	}

}
