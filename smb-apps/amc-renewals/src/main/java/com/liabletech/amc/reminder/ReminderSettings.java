package com.liabletech.amc.reminder;

import java.util.Arrays;
import java.util.List;

/**
 * When renewal reminders go out, in days from the contract's end date (negative is before it), and
 * whether service-visit reminders and automatic sending are on.
 */
public record ReminderSettings(Long accountId, String renewalOffsets, boolean serviceReminders, boolean autoSend) {

	public static final String DEFAULT_OFFSETS = "-30,-15,-7,0,7,30";

	public static ReminderSettings defaults(long accountId) {
		return new ReminderSettings(accountId, DEFAULT_OFFSETS, true, true);
	}

	public List<Integer> offsets() {
		return parse(renewalOffsets);
	}

	/** "-30, -15, 0, 7" as sorted day offsets; anything unreadable is skipped. */
	public static List<Integer> parse(String text) {
		if (text == null) {
			return List.of();
		}
		return Arrays.stream(text.split("[,;\\s]+"))
			.map(String::trim)
			.filter((s) -> s.matches("[+-]?\\d{1,3}"))
			.map(Integer::valueOf)
			.filter((d) -> d >= -90 && d <= 90)
			.distinct()
			.sorted()
			.toList();
	}

}
