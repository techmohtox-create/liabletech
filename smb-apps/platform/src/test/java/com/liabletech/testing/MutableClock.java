package com.liabletech.testing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** A clock tests can move forward, to check due dates, reminders and renewals. */
public class MutableClock extends Clock {

	private volatile Instant now;

	private final ZoneId zone;

	public MutableClock(Instant start, ZoneId zone) {
		this.now = start;
		this.zone = zone;
	}

	/** Noon IST on the given day. */
	public static MutableClock at(LocalDate day) {
		ZoneId india = ZoneId.of("Asia/Kolkata");
		return new MutableClock(day.atTime(12, 0).atZone(india).toInstant(), india);
	}

	public void advance(Duration duration) {
		now = now.plus(duration);
	}

	public void advanceDays(long days) {
		advance(Duration.ofDays(days));
	}

	public void set(LocalDate day) {
		now = day.atTime(12, 0).atZone(zone).toInstant();
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(now, zone);
	}

	@Override
	public Instant instant() {
		return now;
	}

}
