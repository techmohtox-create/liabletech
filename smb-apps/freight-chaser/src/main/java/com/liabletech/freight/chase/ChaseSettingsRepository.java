package com.liabletech.freight.chase;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.account.AccountCreated;

@Repository
public class ChaseSettingsRepository {

	private final JdbcClient jdbc;

	public ChaseSettingsRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public ChaseSettings get(long accountId) {
		return jdbc.sql("SELECT * FROM chase_settings WHERE account_id = ?")
			.param(accountId)
			.query(ChaseSettings.class)
			.optional()
			.orElseGet(() -> ChaseSettings.defaults(accountId));
	}

	public void save(ChaseSettings settings) {
		int updated = jdbc.sql("""
				UPDATE chase_settings SET enabled = :enabled, reminder_days = :reminderDays,
				    repeat_every_days = :repeatEveryDays, send_whatsapp = :sendWhatsapp, send_email = :sendEmail,
				    default_gst_mode = :defaultGstMode, default_gst_rate = :defaultGstRate,
				    invoice_prefix = :invoicePrefix, invoice_terms = :invoiceTerms, owner_digest = :ownerDigest
				WHERE account_id = :accountId
				""").paramSource(settings).update();
		if (updated == 0) {
			jdbc.sql("""
					INSERT INTO chase_settings (account_id, enabled, reminder_days, repeat_every_days, send_whatsapp,
					    send_email, default_gst_mode, default_gst_rate, invoice_prefix, invoice_terms, owner_digest)
					VALUES (:accountId, :enabled, :reminderDays, :repeatEveryDays, :sendWhatsapp, :sendEmail,
					    :defaultGstMode, :defaultGstRate, :invoicePrefix, :invoiceTerms, :ownerDigest)
					""").paramSource(settings).update();
		}
	}

	@EventListener
	void onAccountCreated(AccountCreated event) {
		save(ChaseSettings.defaults(event.accountId()));
	}

}
