package com.liabletech.platform.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.liabletech.platform.account.AppUser;

/**
 * The signed-in user. Controllers take it with {@code @AuthenticationPrincipal CurrentUser user}
 * and scope every query to {@link #accountId()}.
 */
public final class CurrentUser implements UserDetails {

	private final long userId;

	private final long accountId;

	private final String name;

	private final String email;

	private final String role;

	private final boolean active;

	private transient String passwordHash;

	public CurrentUser(AppUser user) {
		this.userId = user.id();
		this.accountId = user.accountId();
		this.name = user.name();
		this.email = user.email();
		this.role = user.role();
		this.active = user.active();
		this.passwordHash = user.passwordHash();
	}

	public long userId() {
		return userId;
	}

	public long accountId() {
		return accountId;
	}

	public String name() {
		return name;
	}

	public String email() {
		return email;
	}

	public boolean isOwner() {
		return AppUser.OWNER.equals(role);
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return active;
	}

}
