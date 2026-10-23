package com.liabletech.payoutcheck.audit;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.liabletech.payoutcheck.common.PageResult;
import com.liabletech.payoutcheck.security.CurrentUser;
import com.liabletech.payoutcheck.security.Roles;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

	private final AuditService audit;

	public AuditController(AuditService audit) {
		this.audit = audit;
	}

	@GetMapping
	@PreAuthorize(Roles.OWNER)
	public PageResult<AuditEvent> list(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return this.audit.list(user.orgId(), PageResult.clampPage(page), PageResult.clampSize(size));
	}

}
