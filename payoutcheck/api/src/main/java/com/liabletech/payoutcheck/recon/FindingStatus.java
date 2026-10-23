package com.liabletech.payoutcheck.recon;

/**
 * OPEN: found, not acted on. DISPUTED: raised with the platform. RECOVERED: money came back
 * (fully or partly). REJECTED: the platform said no. IGNORED: reviewed and accepted as correct.
 */
public enum FindingStatus {

	OPEN, DISPUTED, RECOVERED, REJECTED, IGNORED;

	public boolean closed() {
		return this == RECOVERED || this == REJECTED || this == IGNORED;
	}

}
