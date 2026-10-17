package com.liabletech.bff.contact;

import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.liabletech.bff.notify.LeadNotifier;

@Service
public class ContactService {

	private static final Logger log = LoggerFactory.getLogger(ContactService.class);

	private final JdbcClient jdbcClient;

	private final LeadNotifier notifier;

	public ContactService(JdbcClient jdbcClient, LeadNotifier notifier) {
		this.jdbcClient = jdbcClient;
		this.notifier = notifier;
	}

	public String submit(ContactRequest request) {
		String reference = "LT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
		if (StringUtils.hasText(request.website())) {
			log.debug("Dropped contact enquiry {} (honeypot filled)", reference);
			return reference;
		}
		jdbcClient
			.sql("""
					INSERT INTO contact_enquiry (reference, name, email, company, service, message, stage, timeline, budget, source)
					VALUES (:reference, :name, :email, :company, :service, :message, :stage, :timeline, :budget, :source)
					""")
			.param("reference", reference)
			.param("name", request.name().trim())
			.param("email", request.email().trim())
			.param("company", trimToNull(request.company()))
			.param("service", trimToNull(request.service()))
			.param("message", request.message().trim())
			.param("stage", trimToNull(request.stage()))
			.param("timeline", trimToNull(request.timeline()))
			.param("budget", trimToNull(request.budget()))
			.param("source", trimToNull(request.source()))
			.update();
		log.info("Stored contact enquiry {}", reference);
		notifier.enquiryReceived(reference, request);
		return reference;
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

}
