package com.liabletech.payoutcheck.outlet;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.liabletech.payoutcheck.common.Platform;
import com.liabletech.payoutcheck.security.CurrentUser;
import com.liabletech.payoutcheck.security.Roles;

@RestController
@RequestMapping("/api/outlets")
public class OutletController {

	private final OutletService outlets;

	public OutletController(OutletService outlets) {
		this.outlets = outlets;
	}

	@GetMapping
	public List<Outlet> list(@AuthenticationPrincipal CurrentUser user) {
		return this.outlets.list(user.orgId());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(Roles.OWNER)
	public Outlet create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody OutletRequest request) {
		return this.outlets.create(user, request);
	}

	@PutMapping("/{id}")
	@PreAuthorize(Roles.OWNER)
	public Outlet update(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@Valid @RequestBody OutletRequest request) {
		return this.outlets.update(user, id, request);
	}

	@GetMapping("/{id}/terms")
	public List<PlatformTerms> terms(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
		return this.outlets.terms(user.orgId(), id);
	}

	@PutMapping("/{id}/terms/{platform}")
	@PreAuthorize(Roles.EDITOR)
	public PlatformTerms saveTerms(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@PathVariable String platform, @Valid @RequestBody TermsRequest request) {
		return this.outlets.saveTerms(user, id, Platform.parse(platform), request);
	}

}
