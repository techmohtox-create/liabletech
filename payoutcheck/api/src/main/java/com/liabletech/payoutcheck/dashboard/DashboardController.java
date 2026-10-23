package com.liabletech.payoutcheck.dashboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.liabletech.payoutcheck.dashboard.DashboardService.Dashboard;
import com.liabletech.payoutcheck.security.CurrentUser;

@RestController
public class DashboardController {

	private final DashboardService dashboard;

	public DashboardController(DashboardService dashboard) {
		this.dashboard = dashboard;
	}

	@GetMapping("/api/dashboard")
	public Dashboard dashboard(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) Long outletId) {
		return this.dashboard.build(user.orgId(), outletId);
	}

}
