package com.liabletech.payoutcheck.ingest;

import java.util.Locale;

public enum OrderStatus {

	DELIVERED, CANCELLED, REJECTED, OTHER;

	/** Maps the many ways exports spell a status onto the four the engine reasons about. */
	public static OrderStatus parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return DELIVERED;
		}
		String text = raw.toLowerCase(Locale.ROOT);
		if (text.contains("reject") || text.contains("timed out") || text.contains("timeout")) {
			return REJECTED;
		}
		if (text.contains("cancel")) {
			return CANCELLED;
		}
		if (text.contains("deliver") || text.contains("complete") || text.contains("success") || text.contains("picked")
				|| text.equals("settled") || text.equals("paid")) {
			return DELIVERED;
		}
		return OTHER;
	}

}
