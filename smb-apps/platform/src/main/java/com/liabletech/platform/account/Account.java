package com.liabletech.platform.account;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.springframework.util.StringUtils;

/** A business using one of the apps: a fleet, a builder, an exporter, a service business or a shop. */
public record Account(Long id, String name, String ownerName, String phone, String email, String address, String gstin,
		String pan, String upiId, String bankAccountName, String bankAccountNumber, String bankIfsc, String bankName,
		String whatsappPhoneNumberId, String planCode, LocalDate trialEndsOn, OffsetDateTime createdAt) {

	/** The two-digit GST state code from the GSTIN (27 is Maharashtra), or null without a GSTIN. */
	public String stateCode() {
		return gstStateCode(gstin);
	}

	public boolean hasBankDetails() {
		return StringUtils.hasText(bankAccountNumber) && StringUtils.hasText(bankIfsc);
	}

	public static String gstStateCode(String gstin) {
		return gstin != null && gstin.length() >= 2 && Character.isDigit(gstin.charAt(0)) ? gstin.substring(0, 2) : null;
	}

}
