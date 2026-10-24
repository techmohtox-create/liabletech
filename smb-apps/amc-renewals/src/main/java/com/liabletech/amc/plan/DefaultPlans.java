package com.liabletech.amc.plan;

import java.math.BigDecimal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.liabletech.amc.reminder.ReminderSettings;
import com.liabletech.amc.reminder.ReminderSettingsRepository;
import com.liabletech.platform.account.AccountCreated;

/**
 * The plans most AC and RO service businesses sell, and the standard reminder schedule. Prices are
 * a starting point to edit, not advice.
 */
@Component
public class DefaultPlans {

	private final Plans plans;

	private final ReminderSettingsRepository settings;

	public DefaultPlans(Plans plans, ReminderSettingsRepository settings) {
		this.plans = plans;
		this.settings = settings;
	}

	@EventListener
	public void onAccountCreated(AccountCreated event) {
		long account = event.accountId();
		plans.create(account, "AC Care AMC", "AC", BigDecimal.valueOf(1499), 12, 3,
				"3 services a year: wet cleaning of indoor and outdoor units, gas pressure check. Parts and gas refill extra.");
		plans.create(account, "AC Comprehensive AMC", "AC", BigDecimal.valueOf(3999), 12, 3,
				"3 services a year, gas top-up and labour for all repairs included. Major parts extra.");
		plans.create(account, "RO Care AMC", "RO", BigDecimal.valueOf(2499), 12, 4,
				"4 services a year with sediment and carbon filter changes. Membrane extra.");
		plans.create(account, "RO Gold AMC", "RO", BigDecimal.valueOf(3999), 12, 4,
				"4 services a year with all filters and the RO membrane included.");
		settings.save(ReminderSettings.defaults(account));
	}

}
