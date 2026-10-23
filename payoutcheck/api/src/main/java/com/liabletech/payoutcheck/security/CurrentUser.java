package com.liabletech.payoutcheck.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.liabletech.payoutcheck.account.AppUser;
import com.liabletech.payoutcheck.account.Role;

/** The signed-in user. {@link #orgId()} scopes every query a request makes. */
public record CurrentUser(long id, long orgId, String email, String fullName, Role role, String passwordHash, boolean active)
		implements UserDetails {

	public static CurrentUser of(AppUser user) {
		return new CurrentUser(user.id(), user.orgId(), user.email(), user.fullName(), user.role(), user.passwordHash(),
				user.active());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
	}

	@Override
	public String getPassword() {
		return this.passwordHash;
	}

	@Override
	public String getUsername() {
		return this.email;
	}

	@Override
	public boolean isEnabled() {
		return this.active;
	}

	@Override
	public String toString() {
		return "CurrentUser[" + this.email + "]";
	}

}
