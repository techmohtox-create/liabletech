package com.liabletech.bff.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.liabletech.bff.contact.ContactRequest;
import com.liabletech.bff.waitlist.WaitlistRequest;

class LeadNotifierTests {

	private final JavaMailSender sender = mock(JavaMailSender.class);

	private final LeadNotifier notifier = new LeadNotifier(provider(sender), "contact@liabletech.com",
			"contact@liabletech.com");

	@Test
	void emailsEnquiryToTheTeamWithReplyToTheVisitor() {
		notifier.enquiryReceived("LT-1234ABCD", new ContactRequest("Asha\r\nBcc: x@evil.example", "asha@example.com",
				"Rao Agro", "AI automation", "We need invoice automation.", "Live system that needs work",
				"Within 1–3 months", null, "/contact | source=linkedin", null));

		SimpleMailMessage mail = sent();
		assertThat(mail.getTo()).containsExactly("contact@liabletech.com");
		assertThat(mail.getFrom()).isEqualTo("contact@liabletech.com");
		assertThat(mail.getReplyTo()).isEqualTo("asha@example.com");
		assertThat(mail.getSubject()).isEqualTo("New enquiry LT-1234ABCD: AI automation from Asha Bcc: x@evil.example")
			.doesNotContain("\n");
		assertThat(mail.getText()).contains("Company: Rao Agro", "Budget: -", "Came from: /contact | source=linkedin",
				"Message:\nWe need invoice automation.");
	}

	@Test
	void emailsWaitlistSignUp() {
		notifier.waitlistJoined("WL-ABCD1234", new WaitlistRequest(null, "ops@clinic.example", "City Clinic",
				"medflow-ai", null));

		SimpleMailMessage mail = sent();
		assertThat(mail.getSubject()).isEqualTo("Waitlist medflow-ai: ops@clinic.example");
		assertThat(mail.getText()).contains("Product: medflow-ai", "Name: -", "Organisation: City Clinic");
	}

	@Test
	void mailFailureDoesNotPropagate() {
		doThrow(new MailSendException("SMTP down")).when(sender).send(any(SimpleMailMessage.class));

		notifier.waitlistJoined("WL-1", new WaitlistRequest(null, "a@b.example", null, "erp", null));
	}

	@Test
	void skipsWhenMailIsNotConfigured() {
		new LeadNotifier(provider(null), "contact@liabletech.com", "")
			.waitlistJoined("WL-2", new WaitlistRequest(null, "a@b.example", null, "erp", null));
	}

	private SimpleMailMessage sent() {
		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(sender).send(captor.capture());
		return captor.getValue();
	}

	@SuppressWarnings("unchecked")
	private static ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
		ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
		org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(sender);
		return provider;
	}

}
