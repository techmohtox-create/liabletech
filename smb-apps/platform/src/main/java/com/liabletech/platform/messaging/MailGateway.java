package com.liabletech.platform.messaging;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;

/**
 * Sends email with attachments when SMTP is configured ({@code SPRING_MAIL_HOST}); otherwise the
 * message is only recorded in the outbox, so nothing breaks on a laptop without a mail server.
 */
@Component
class MailGateway {

	private static final Logger log = LoggerFactory.getLogger(MailGateway.class);

	private final ObjectProvider<JavaMailSender> mailSender;

	private final FileStore files;

	private final String from;

	MailGateway(ObjectProvider<JavaMailSender> mailSender, FileStore files, @Value("${app.mail.from}") String from) {
		this.mailSender = mailSender;
		this.files = files;
		this.from = from;
	}

	Result send(Outgoing message, String replyTo, String senderName) {
		JavaMailSender sender = mailSender.getIfAvailable();
		if (sender == null) {
			return new Result(true, true, null);
		}
		try {
			MimeMessage mime = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mime, !message.attachments().isEmpty(), "UTF-8");
			helper.setFrom(from, senderName);
			helper.setTo(message.to());
			if (replyTo != null) {
				helper.setReplyTo(replyTo);
			}
			helper.setSubject(message.subject());
			helper.setText(message.body(), false);
			for (Outgoing.Attachment attachment : message.attachments()) {
				StoredFile file = files.get(attachment.fileId());
				helper.addAttachment(attachment.filename(), new ByteArrayResource(files.read(file)), file.contentType());
			}
			sender.send(mime);
			return new Result(true, false, null);
		}
		catch (MessagingException | MailException | java.io.UnsupportedEncodingException ex) {
			log.warn("Email to {} failed: {}", message.to(), ex.getMessage());
			return new Result(false, false, ex.getMessage());
		}
	}

	record Result(boolean ok, boolean simulated, String error) {
	}

}
