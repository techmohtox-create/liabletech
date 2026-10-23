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

	record PlanSummary(String planName, String price, BigDecimal monthlyAmount, List<String> details) {
	}

}
