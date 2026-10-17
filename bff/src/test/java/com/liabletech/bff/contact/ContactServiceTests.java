package com.liabletech.bff.contact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class ContactServiceTests {

	@Autowired
	private ContactService contactService;

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void storesEnquiry() {
		String reference = contactService
			.submit(new ContactRequest("Asha Rao", "asha@example.com", " ", "cloud", "We need a cloud migration.",
				"Live system that needs work", "Within 1–3 months", " ", "/contact | source=linkedin", null));

		var row = jdbcClient.sql("SELECT name, email, company, service, stage, timeline, budget, source FROM contact_enquiry WHERE reference = ?")
			.param(reference)
			.query()
			.singleRow();
		assertThat(row).containsEntry("name", "Asha Rao")
			.containsEntry("email", "asha@example.com")
			.containsEntry("company", null)
			.containsEntry("service", "cloud")
			.containsEntry("stage", "Live system that needs work")
			.containsEntry("timeline", "Within 1–3 months")
			.containsEntry("budget", null)
			.containsEntry("source", "/contact | source=linkedin");
	}

	@Test
	void dropsEnquiryWhenHoneypotIsFilled() {
		String reference = contactService
			.submit(new ContactRequest("Bot", "bot@example.com", null, null, "Buy cheap things now.", null, null, null, null,
				"http://spam.example"));

		Long stored = jdbcClient.sql("SELECT COUNT(*) FROM contact_enquiry WHERE reference = ?")
			.param(reference)
			.query(Long.class)
			.single();
		assertThat(stored).isZero();
	}

}
