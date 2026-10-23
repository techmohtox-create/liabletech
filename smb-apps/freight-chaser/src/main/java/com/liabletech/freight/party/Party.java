package com.liabletech.freight.party;

import java.time.OffsetDateTime;

import com.liabletech.platform.account.Account;

/** A transporter, broker or consignor who owes freight. */
public record Party(Long id, Long accountId, String name, String contactPerson, String phone, String email,
		String gstin, String address, int creditDays, OffsetDateTime createdAt) {

	public String stateCode() {
		return Account.gstStateCode(gstin);
	}

}
