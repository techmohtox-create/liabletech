package com.liabletech.bff.waitlist;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class WaitlistServiceTests {

	@Autowired
	private WaitlistService waitlistService;

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void storesSignUpOncePerProduct() {
		String first = waitlistService.join(new WaitlistRequest("Dr Mehta", "Ops@Clinic.example", "City Clinic", "medflow-ai", null));
		String again = waitlistService.join(new WaitlistRequest(null, "ops@clinic.example", null, "medflow-ai", null));

		assertThat(again).isEqualTo(first);
		Long rows = jdbcClient.sql("SELECT COUNT(*) FROM waitlist_signup WHERE email = 'ops@clinic.example'")
			.query(Long.class)
			.single();
		assertThat(rows).isEqualTo(1);
	}

	@Test
	void dropsSignUpWhenHoneypotIsFilled() {
		waitlistService.join(new WaitlistRequest("Bot", "bot@spam.example", null, "erp", "http://spam.example"));

		Long rows = jdbcClient.sql("SELECT COUNT(*) FROM waitlist_signup WHERE email = 'bot@spam.example'")
			.query(Long.class)
			.single();
		assertThat(rows).isZero();
	}

	@Test
	void acceptsTenderCheckSignUps() {
		String reference = waitlistService.join(new WaitlistRequest("Ravi", "bids@contractor.example", "Ravi Constructions", "tendercheck", null));

		String product = jdbcClient.sql("SELECT product FROM waitlist_signup WHERE reference = :reference")
			.param("reference", reference)
			.query(String.class)
			.single();
		assertThat(product).isEqualTo("tendercheck");
	}

}
