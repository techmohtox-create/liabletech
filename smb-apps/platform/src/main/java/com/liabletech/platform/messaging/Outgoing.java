package com.liabletech.platform.messaging;

import java.util.ArrayList;
import java.util.List;

/**
 * A WhatsApp message or email to send. Build one with {@link #whatsApp} or {@link #email}, then
 * add attachments, a WhatsApp template and the record it is about:
 *
 * <pre>
 * messenger.send(Outgoing.whatsApp(accountId, phone, text)
 *     .attach(pdfId, "Invoice FC-0012.pdf")
 *     .template("payment_reminder", partyName, invoiceNo, amount)
 *     .about("invoice", invoiceId));
 * </pre>
 */
public record Outgoing(Channel channel, Long accountId, String to, String subject, String body,
		List<Attachment> attachments, Template template, String relatedType, Long relatedId) {

	public enum Channel {

		WHATSAPP, EMAIL

	}

	/** A stored file sent along: a PDF goes as a document, a photo as an image. */
	public record Attachment(long fileId, String filename) {
	}

	/**
	 * An approved WhatsApp template, used when {@code app.whatsapp.use-templates} is on. The body
	 * parameters fill {{1}}, {{2}}... in order; the first attachment becomes the document header.
	 */
	public record Template(String name, List<String> bodyParameters) {
	}

	/** A WhatsApp text; {@code accountId} is null only for replies to numbers no business knows. */
	public static Outgoing whatsApp(Long accountId, String phone, String text) {
		return new Outgoing(Channel.WHATSAPP, accountId, phone, null, text, List.of(), null, null, null);
	}

	public static Outgoing email(long accountId, String to, String subject, String body) {
		return new Outgoing(Channel.EMAIL, accountId, to, subject, body, List.of(), null, null, null);
	}

	public Outgoing attach(long fileId, String filename) {
		List<Attachment> more = new ArrayList<>(attachments);
		more.add(new Attachment(fileId, filename));
		return new Outgoing(channel, accountId, to, subject, body, List.copyOf(more), template, relatedType, relatedId);
	}

	public Outgoing template(String name, String... bodyParameters) {
		return new Outgoing(channel, accountId, to, subject, body, attachments,
				new Template(name, List.of(bodyParameters)), relatedType, relatedId);
	}

	public Outgoing about(String type, Long id) {
		return new Outgoing(channel, accountId, to, subject, body, attachments, template, type, id);
	}

}
