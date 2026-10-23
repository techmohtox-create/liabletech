package com.liabletech.payoutcheck.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Locks an email out for 15 minutes after 5 failed sign-ins in a row. */
@Component
public class LoginAttempts {

	static final int MAX_FAILURES = 5;

	static final Duration LOCKOUT = Duration.ofMinutes(15);

	private final Map<String, Attempts> byEmail = new ConcurrentHashMap<>();

	private final Clock clock;

	public LoginAttempts() {
		this(Clock.systemUTC());
	}

	LoginAttempts(Clock clock) {
		this.clock = clock;
	}

	public boolean isLocked(String email) {
		Attempts attempts = this.byEmail.get(email);
		return attempts != null && attempts.failures >= MAX_FAILURES
				&& attempts.lastFailure.plus(LOCKOUT).isAfter(this.clock.instant());
	}

	public void failed(String email) {
		Instant now = this.clock.instant();
		this.byEmail.compute(email, (key, previous) -> {
			if (previous == null || previous.lastFailure.plus(LOCKOUT).isBefore(now)) {
				return new Attempts(1, now);
			}
			return new Attempts(previous.failures + 1, now);
		});
	}

	public void succeeded(String email) {
		this.byEmail.remove(email);
	}

	private record Attempts(int failures, Instant lastFailure) {
	}

}
