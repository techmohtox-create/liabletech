package com.liabletech.payoutcheck.account;

/**
 * OWNER manages the plan, team and everything below. MANAGER imports files, edits terms and works
 * disputes. VIEWER (an accountant, a partner) can read everything and export, but change nothing.
 */
public enum Role {

	OWNER, MANAGER, VIEWER

}
