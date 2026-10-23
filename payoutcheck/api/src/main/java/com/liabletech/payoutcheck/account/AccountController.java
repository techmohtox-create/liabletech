package com.liabletech.payoutcheck.account;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.liabletech.payoutcheck.outlet.OutletService;
import com.liabletech.payoutcheck.security.CurrentUser;
import com.liabletech.payoutcheck.security.Roles;

@RestController
public class AccountController {

	private final AccountService accounts;

	private final OutletService outlets;

	public AccountController(AccountService accounts, OutletService outlets) {
		this.accounts = accounts;
		this.outlets = outlets;
	}

	@GetMapping("/api/plans")
	public List<PlanView> plans() {
		return Arrays.stream(Plan.values()).map(PlanView::of).toList();
	}

	@GetMapping("/api/organization")
	public OrganizationView organization(@AuthenticationPrincipal CurrentUser user) {
		return view(this.accounts.organization(user.orgId()), user.orgId());
	}

	@PutMapping("/api/organization")
	@PreAuthorize(Roles.OWNER)
	public OrganizationView rename(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody RenameRequest request) {
		return view(this.accounts.rename(user, request.name()), user.orgId());
	}

	@PutMapping("/api/organization/plan")
	@PreAuthorize(Roles.OWNER)
	public OrganizationView changePlan(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody PlanRequest request) {
		return view(this.accounts.changePlan(user, request.plan(), this.outlets.activeCount(user.orgId())), user.orgId());
	}

	@GetMapping("/api/users")
	public List<UserView> users(@AuthenticationPrincipal CurrentUser user) {
		return this.accounts.users(user.orgId()).stream().map(UserView::of).toList();
	}

	@PostMapping("/api/users")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(Roles.OWNER)
	public UserView addUser(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody NewUserRequest request) {
		return UserView.of(this.accounts.addUser(user, request.email(), request.fullName(), request.password(), request.role()));
	}

	@PutMapping("/api/users/{id}")
	@PreAuthorize(Roles.OWNER)
	public UserView updateUser(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@Valid @RequestBody UpdateUserRequest request) {
		return UserView.of(this.accounts.updateUser(user, id, request.role(), request.active()));
	}

	@PostMapping("/api/users/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody PasswordRequest request) {
		this.accounts.changePassword(user, request.currentPassword(), request.newPassword());
	}

	private OrganizationView view(Organization org, long orgId) {
		return new OrganizationView(org.id(), org.name(), PlanView.of(org.plan()), this.outlets.activeCount(orgId),
				this.accounts.users(orgId).stream().filter(AppUser::active).count(), org.createdAt());
	}

	public record PlanView(String code, String label, int monthlyPriceInr, int maxOutlets, int maxUsers, boolean disputeLetters) {

		static PlanView of(Plan plan) {
			return new PlanView(plan.name(), plan.label(), plan.monthlyPriceInr(), plan.maxOutlets(), plan.maxUsers(),
					plan.disputeLetters());
		}

	}

	public record OrganizationView(long id, String name, PlanView plan, int activeOutlets, long activeUsers,
			OffsetDateTime createdAt) {
	}

	public record UserView(long id, String email, String fullName, Role role, boolean active, OffsetDateTime createdAt,
			OffsetDateTime lastLoginAt) {

		static UserView of(AppUser user) {
			return new UserView(user.id(), user.email(), user.fullName(), user.role(), user.active(), user.createdAt(),
					user.lastLoginAt());
		}

	}

	public record RenameRequest(@NotBlank @Size(max = 160) String name) {
	}

	public record PlanRequest(@NotNull Plan plan) {
	}

	public record NewUserRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 120) String fullName,
			@NotBlank @Size(min = 8, max = 100) String password, @NotNull Role role) {
	}

	public record UpdateUserRequest(@NotNull Role role, boolean active) {
	}

	public record PasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 100) String newPassword) {
	}

}
