package com.liabletech.bff.notify;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.liabletech.bff.contact.ContactRequest;
import com.liabletech.bff.waitlist.WaitlistRequest;

/**
 * Emails each new enquiry and waitlist sign-up to the team inbox. Runs after the row is stored and
 * off the request thread, so a slow or failing mail server never loses a lead or delays the visitor.
 */
@Component
public class LeadNotifier {

	private static final Logger log = LoggerFactory.getLogger(LeadNotifier.class);

	private final ObjectProvider<JavaMailSender> mailSender;

	private final String notifyTo;

	private final String from;

	public LeadNotifier(ObjectProvider<JavaMailSender> mailSender,
			@Value("${liabletech.leads.notify-to}") String notifyTo,
			@Value("${spring.mail.username:}") String mailUsername) {
		this.mailSender = mailSender;
		this.notifyTo = notifyTo;
		this.from = StringUtils.hasText(mailUsername) ? mailUsername : notifyTo;
	}

	@Async
	public void enquiryReceived(String reference, ContactRequest request) {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("Reference", reference);
		fields.put("Name", request.name());
		fields.put("Email", request.email());
		fields.put("Company", request.company());
		fields.put("Needs", request.service());
		fields.put("Stage", request.stage());
		fields.put("Timeline", request.timeline());
		fields.put("Budget", request.budget());
		fields.put("Came from", request.source());
		String subject = "New enquiry " + reference + ": " + orDash(request.service()) + " from " + request.name();
		send(subject, body("A new enquiry arrived on liabletech.com.", fields, request.message()), request.email());
	}

	@Async
	public void waitlistJoined(String reference, WaitlistRequest request) {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("Reference", reference);
		fields.put("Product", request.product());
		fields.put("Name", request.name());
		fields.put("Email", request.email());
		fields.put("Organisation", request.company());
		String subject = "Waitlist " + request.product() + ": " + request.email();
		send(subject, body("Someone joined a product waitlist on liabletech.com.", fields, null), request.email());
	}

	private void send(String subject, String text, String replyTo) {
		JavaMailSender sender = mailSender.getIfAvailable();
		if (sender == null) {
			log.info("Mail not configured; skipped notification \"{}\"", oneLine(subject));
			return;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(notifyTo);
		message.setReplyTo(oneLine(replyTo));
		message.setSubject(oneLine(subject));
		message.setText(text);
		try {
			sender.send(message);
			log.info("Sent notification \"{}\" to {}", oneLine(subject), notifyTo);
		}
		catch (MailException ex) {
			// The lead is already stored in the database; the mail is a convenience copy.
			log.error("Could not send notification \"{}\": {}", oneLine(subject), ex.getMessage());
		}
	}

	static String body(String intro, Map<String, String> fields, String message) {
		StringBuilder text = new StringBuilder(intro).append("\n\n");
		fields.forEach((label, value) -> text.append(label).append(": ").append(orDash(value)).append('\n'));
		if (StringUtils.hasText(message)) {
			text.append("\nMessage:\n").append(message.trim()).append('\n');
		}
		text.append("\nReply to this email to answer them directly.\n");
		return text.toString();
	}

	private static String orDash(String value) {
		return StringUtils.hasText(value) ? value.trim() : "-";
	}

	/** Header values must stay on one line, whatever a visitor typed. */
	private static String oneLine(String value) {
		return value == null ? null : value.replaceAll("[\\r\\n]+", " ").trim();
	}

}
