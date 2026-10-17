package com.liabletech.bff.waitlist;

import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.liabletech.bff.notify.LeadNotifier;

@Service
public class WaitlistService {

	private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

	private final JdbcClient jdbcClient;

	private final LeadNotifier notifier;

	public WaitlistService(JdbcClient jdbcClient, LeadNotifier notifier) {
		this.jdbcClient = jdbcClient;
		this.notifier = notifier;
	}

	/** Adds the email to the product's waitlist once; signing up again returns the original reference. */
	public String join(WaitlistRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (StringUtils.hasText(request.website())) {
			log.debug("Dropped waitlist sign-up (honeypot filled)");
			return newReference();
		}
		var existing = jdbcClient.sql("SELECT reference FROM waitlist_signup WHERE email = :email AND product = :product")
			.param("email", email)
			.param("product", request.product())
			.query(String.class)
			.optional();
		if (existing.isPresent()) {
			return existing.get();
		}
		String reference = newReference();
		jdbcClient
			.sql("""
					INSERT INTO waitlist_signup (reference, product, name, email, company)
					VALUES (:reference, :product, :name, :email, :company)
					""")
			.param("reference", reference)
			.param("product", request.product())
			.param("name", trimToNull(request.name()))
			.param("email", email)
			.param("company", trimToNull(request.company()))
			.update();
		log.info("Stored waitlist sign-up {} for {}", reference, request.product());
		notifier.waitlistJoined(reference, request);
		return reference;
	}

	private static String newReference() {
		return "WL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

}
