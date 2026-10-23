package com.liabletech.platform.account;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liabletech.platform.text.Phones;

@Service
public class SignupService {

	private final Accounts accounts;

	private final Users users;

	private final PasswordEncoder passwordEncoder;

	private final PlanCatalog plans;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public SignupService(Accounts accounts, Users users, PasswordEncoder passwordEncoder, PlanCatalog plans,
			ApplicationEventPublisher events, Clock clock) {
		this.accounts = accounts;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.plans = plans;
		this.events = events;
		this.clock = clock;
	}

	/** Creates the business and its owner login, on the app's default plan with a free trial. */
	@Transactional
	public long signUp(SignupForm form) {
		return create(form.getBusinessName().trim(), form.getOwnerName().trim(), form.getPhone(), form.getEmail(),
				form.getPassword(), false);
	}

	/** Same as sign-up, for the demo account that apps create on first start. */
	@Transactional
	public long createDemo(String businessName, String ownerName, String phone, String email, String password) {
		return create(businessName, ownerName, phone, email, password, true);
	}

	public void addStaff(long accountId, String name, String email, String password) {
		users.create(accountId, name, email, passwordEncoder.encode(password), AppUser.STAFF);
	}

	private long create(String businessName, String ownerName, String phone, String email, String password,
			boolean demo) {
		String normalizedPhone = Phones.normalize(phone);
		LocalDate trialEnds = LocalDate.now(clock).plusDays(plans.trialDays());
		long accountId = accounts.create(businessName, ownerName, normalizedPhone != null ? normalizedPhone : phone,
				email.trim().toLowerCase(Locale.ROOT), plans.defaultPlanCode(), trialEnds);
		users.create(accountId, ownerName, email, passwordEncoder.encode(password), AppUser.OWNER);
		events.publishEvent(new AccountCreated(accountId, demo));
		return accountId;
	}

}
