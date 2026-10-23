package com.liabletech.freight.chase;

import java.time.OffsetDateTime;

/** One event in chasing an invoice. */
public record FollowUp(Long id, Long accountId, Long invoiceId, OffsetDateTime happenedAt, String kind, Integer stage,
		String note, Long outboundMessageId) {

	/** The invoice went to the party. */
	public static final String SENT = "SENT";

	/** An automatic reminder. */
	public static final String REMINDER = "REMINDER";

	/** The owner phoned or met the party and noted it. */
	public static final String NOTE = "NOTE";

	/** The party promised a payment date. */
	public static final String PROMISE = "PROMISE";

	public static final String PAYMENT = "PAYMENT";

	public static final String STATUS = "STATUS";

}
