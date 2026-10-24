package com.liabletech.amc.request;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** A renewal or a service visit someone asked for. */
public record ServiceRequest(Long id, Long accountId, Long customerId, Long contractId, Long applianceId, String kind,
		String source, String status, Long planId, LocalDate preferredOn, String preferredSlot, LocalDate scheduledOn,
		Long technicianId, String note, LocalDate doneOn, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

	public static final String RENEWAL = "RENEWAL";

	public static final String SERVICE = "SERVICE";

	public static final String REPAIR = "REPAIR";

	public static final String NEW = "NEW";

	public static final String SCHEDULED = "SCHEDULED";

	public static final String DONE = "DONE";

	public static final String DISMISSED = "DISMISSED";

	public boolean open() {
		return NEW.equals(status) || SCHEDULED.equals(status);
	}

}
