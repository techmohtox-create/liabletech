package com.liabletech.platform.account;

import java.math.BigDecimal;
import java.util.List;

/**
 * How an app charges: the plan a new account starts on and what the current month costs. Payment
 * collection is not part of these apps yet; the settings page shows the amount due.
 */
public interface PlanCatalog {

	String defaultPlanCode();

	default int trialDays() {
		return 14;
	}

	PlanSummary summarize(Account account);

	/** Plans the owner can switch between on the settings page; empty when the app has one plan. */
	default List<PlanOption> options() {
		return List.of();
	}

	record PlanSummary(String planName, String price, BigDecimal monthlyAmount, List<String> details) {
	}

	record PlanOption(String code, String name, String price) {
	}

}
