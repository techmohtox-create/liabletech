package com.liabletech.payoutcheck.account;

import java.util.List;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liabletech.payoutcheck.audit.AuditService;
import com.liabletech.payoutcheck.common.BadRequestException;
import com.liabletech.payoutcheck.common.ConflictException;
import com.liabletech.payoutcheck.common.NotFoundException;
import com.liabletech.payoutcheck.outlet.OutletService;
import com.liabletech.payoutcheck.security.CurrentUser;

@Service
public class AccountService {

	private final AccountRepository accounts;

	private final PasswordEncoder passwordEncoder;

	private final AuditService audit;

	private final OutletService outlets;

	public AccountService(AccountRepository accounts, PasswordEncoder passwordEncoder, AuditService audit,
			OutletService outlets) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.audit = audit;
		this.outlets = outlets;
	}

	/**
	 * Creates a business on the Starter plan with its first owner and first outlet. Returns the
	 * owner's id.
	 */
	@Transactional
	public long signUp(String businessName, String outletName, String fullName, String email, String password) {
		String normalised = normaliseEmail(email);
		if (this.accounts.findUserByEmail(normalised).isPresent()) {
			throw new ConflictException("An account with this email already exists");
		}
		long orgId = this.accounts.insertOrganization(businessName.trim(), Plan.STARTER);
		long userId = this.accounts.insertUser(orgId, normalised, fullName.trim(), this.passwordEncoder.encode(password),
				Role.OWNER);
		this.outlets.createFirst(orgId, outletName == null || outletName.isBlank() ? businessName.trim() : outletName.trim(),
				null);
		this.audit.record(orgId, userId, normalised, "account.signup", "organization", orgId, businessName.trim());
		return userId;
	}

	public Organization organization(long orgId) {
		return this.accounts.findOrganization(orgId).orElseThrow(() -> new NotFoundException("Organisation"));
	}

	@Transactional
	public Organization rename(CurrentUser actor, String name) {
		this.accounts.renameOrganization(actor.orgId(), name.trim());
		this.audit.record(actor, "organization.rename", "organization", actor.orgId(), name.trim());
		return organization(actor.orgId());
	}

	/**
	 * Switches plan. There is no payment step here: billing runs outside the app and an owner records
	 * the plan the business pays for. Downgrading below current usage is refused.
	 */
	@Transactional
	public Organization changePlan(CurrentUser actor, Plan plan, int activeOutlets) {
		if (activeOutlets > plan.maxOutlets()) {
			throw new BadRequestException("%s allows %d outlet(s); deactivate outlets first".formatted(plan.label(),
					plan.maxOutlets()));
		}
		if (this.accounts.countActiveUsers(actor.orgId()) > plan.maxUsers()) {
			throw new BadRequestException("%s allows %d user(s); deactivate users first".formatted(plan.label(), plan.maxUsers()));
		}
		Organization before = organization(actor.orgId());
		this.accounts.updatePlan(actor.orgId(), plan);
		this.audit.record(actor, "organization.plan", "organization", actor.orgId(), before.plan() + " -> " + plan);
		return organization(actor.orgId());
	}

	public List<AppUser> users(long orgId) {
		return this.accounts.listUsers(orgId);
	}

	@Transactional
	public AppUser addUser(CurrentUser actor, String email, String fullName, String password, Role role) {
		Plan plan = organization(actor.orgId()).plan();
		if (this.accounts.countActiveUsers(actor.orgId()) >= plan.maxUsers()) {
			throw new BadRequestException("The %s plan allows %d user(s). Upgrade to add more.".formatted(plan.label(),
					plan.maxUsers()));
		}
		String normalised = normaliseEmail(email);
		if (this.accounts.findUserByEmail(normalised).isPresent()) {
			throw new ConflictException("An account with this email already exists");
		}
		long id = this.accounts.insertUser(actor.orgId(), normalised, fullName.trim(), this.passwordEncoder.encode(password), role);
		this.audit.record(actor, "user.add", "app_user", id, normalised + " as " + role);
		return this.accounts.findUser(actor.orgId(), id).orElseThrow();
	}

	@Transactional
	public AppUser updateUser(CurrentUser actor, long userId, Role role, boolean active) {
		AppUser user = this.accounts.findUser(actor.orgId(), userId).orElseThrow(() -> new NotFoundException("User"));
		boolean losesOwner = user.role() == Role.OWNER && user.active() && (role != Role.OWNER || !active);
		if (losesOwner && this.accounts.countActiveOwners(actor.orgId()) <= 1) {
			throw new BadRequestException("The business needs at least one active owner");
		}
		if (active && !user.active()) {
			Plan plan = organization(actor.orgId()).plan();
			if (this.accounts.countActiveUsers(actor.orgId()) >= plan.maxUsers()) {
				throw new BadRequestException("The %s plan allows %d user(s)".formatted(plan.label(), plan.maxUsers()));
			}
		}
		this.accounts.updateUser(actor.orgId(), userId, role, active);
		this.audit.record(actor, "user.update", "app_user", userId, user.email() + ": " + role + (active ? "" : ", deactivated"));
		return this.accounts.findUser(actor.orgId(), userId).orElseThrow();
	}

	@Transactional
	public void changePassword(CurrentUser actor, String currentPassword, String newPassword) {
		AppUser user = this.accounts.findUser(actor.orgId(), actor.id()).orElseThrow(() -> new NotFoundException("User"));
		if (!this.passwordEncoder.matches(currentPassword, user.passwordHash())) {
			throw new BadRequestException("Current password is incorrect");
		}
		this.accounts.updatePassword(actor.id(), this.passwordEncoder.encode(newPassword));
		this.audit.record(actor, "user.password", "app_user", actor.id(), null);
	}

	static String normaliseEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
