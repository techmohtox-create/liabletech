package com.liabletech.payoutcheck.security;

/** {@code @PreAuthorize} expressions, kept in one place. */
public final class Roles {

	public static final String OWNER = "hasRole('OWNER')";

	public static final String EDITOR = "hasAnyRole('OWNER', 'MANAGER')";

	private Roles() {
	}

}
