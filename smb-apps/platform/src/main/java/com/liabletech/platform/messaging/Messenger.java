package com.liabletech.platform.messaging;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.text.Phones;
import com.liabletech.platform.whatsapp.WhatsAppGateway;

/**
 * The one way the apps send anything. Every message is recorded in the outbox with its outcome,
 * so the owner can always see what went to whom, including what the simulator only pretended to
 * send.
 */
@Service
public class Messenger {

	private final WhatsAppGateway whatsApp;

	private final MailGateway mail;

	private final Outbox outbox;

	private final Accounts accounts;

	private final Clock clock;

	public Messenger(WhatsAppGateway whatsApp, MailGateway mail, Outbox outbox, Accounts accounts, Clock clock) {
		this.whatsApp = whatsApp;
		this.mail = mail;
		this.outbox = outbox;
		this.accounts = accounts;
		this.clock = clock;
	}

	public OutboundMessage send(Outgoing message) {
		Account account = message.accountId() == null ? null : accounts.get(message.accountId());
		OffsetDateTime now = OffsetDateTime.now(clock);
		if (!StringUtils.hasText(message.to())) {
			return outbox.get(outbox.record(message, OutboundMessage.FAILED, null, "No recipient", now));
		}
		return switch (message.channel()) {
			case WHATSAPP -> sendWhatsApp(message, account, now);
			case EMAIL -> sendEmail(message, account, now);
		};
	}

	private OutboundMessage sendWhatsApp(Outgoing message, Account account, OffsetDateTime now) {
		String phone = Phones.normalize(message.to());
		if (phone == null) {
			return outbox.get(outbox.record(message, OutboundMessage.FAILED, null, "Not a valid phone number", now));
		}
		Outgoing normalized = new Outgoing(message.channel(), message.accountId(), phone, message.subject(),
				message.body(), message.attachments(), message.template(), message.relatedType(), message.relatedId());
		WhatsAppGateway.Result result = whatsApp.send(normalized,
				account == null ? null : account.whatsappPhoneNumberId());
		String status = !result.ok() ? OutboundMessage.FAILED
				: result.simulated() ? OutboundMessage.SIMULATED : OutboundMessage.SENT;
		return outbox.get(outbox.record(normalized, status, result.providerMessageId(), result.error(), now));
	}

	private OutboundMessage sendEmail(Outgoing message, Account account, OffsetDateTime now) {
		if (account == null) {
			return outbox.get(outbox.record(message, OutboundMessage.FAILED, null, "Email needs an account", now));
		}
		MailGateway.Result result = mail.send(message, account.email(), account.name());
		String status = !result.ok() ? OutboundMessage.FAILED
				: result.simulated() ? OutboundMessage.SIMULATED : OutboundMessage.SENT;
		return outbox.get(outbox.record(message, status, null, result.error(), now));
	}

}
