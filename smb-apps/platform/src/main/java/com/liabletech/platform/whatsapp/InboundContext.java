package com.liabletech.platform.whatsapp;

import java.util.Optional;

import com.liabletech.platform.account.Account;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.messaging.Messenger;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.messaging.Outgoing;

/** A received message together with its business, its file and a way to reply to the sender. */
public final class InboundContext {

	private final InboundMessage message;

	private final Account account;

	private final FileStore files;

	private final Messenger messenger;

	InboundContext(InboundMessage message, Account account, FileStore files, Messenger messenger) {
		this.message = message;
		this.account = account;
		this.files = files;
		this.messenger = messenger;
	}

	public InboundMessage message() {
		return message;
	}

	public Account account() {
		return account;
	}

	public long accountId() {
		return account.id();
	}

	public String from() {
		return message.fromPhone();
	}

	public Optional<StoredFile> file() {
		return message.fileId() == null ? Optional.empty() : files.find(message.fileId());
	}

	/** Sends a WhatsApp text back to whoever wrote, recorded against the given record. */
	public OutboundMessage reply(String text, String relatedType, Long relatedId) {
		return messenger.send(Outgoing.whatsApp(account.id(), message.fromPhone(), text).about(relatedType, relatedId));
	}

	public OutboundMessage reply(String text) {
		return reply(text, "wa_inbound", message.id());
	}

}
