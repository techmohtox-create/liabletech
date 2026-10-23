package com.liabletech.platform.whatsapp;

import java.time.OffsetDateTime;

/**
 * A WhatsApp message received from a driver, supervisor, technician, mechanic or customer. For
 * photos and documents, {@code textBody} holds the caption.
 */
public record InboundMessage(Long id, Long accountId, String waMessageId, String fromPhone, String toPhoneNumberId,
		String contactName, String messageType, String textBody, String mediaId, String mediaMimeType,
		String mediaFilename, Long fileId, String contextWaMessageId, OffsetDateTime receivedAt, String status,
		int attempts, String error, OffsetDateTime processedAt) {

	public static final String RECEIVED = "RECEIVED";

	public static final String PROCESSED = "PROCESSED";

	public static final String FAILED = "FAILED";

	public static final String UNROUTED = "UNROUTED";

	public boolean isText() {
		return "text".equals(messageType) || "button".equals(messageType) || "interactive".equals(messageType);
	}

	public boolean isImage() {
		return "image".equals(messageType);
	}

	public boolean isAudio() {
		return "audio".equals(messageType);
	}

	public boolean isDocument() {
		return "document".equals(messageType);
	}

	/** A photo, or a document that is a PDF or an image (people often forward scans as documents). */
	public boolean isPhotoOrPdf() {
		if (isImage()) {
			return true;
		}
		return isDocument() && mediaMimeType != null
				&& (mediaMimeType.startsWith("image/") || mediaMimeType.equals("application/pdf"));
	}

	public boolean hasMedia() {
		return mediaId != null || fileId != null;
	}

	/** The message text or caption, trimmed; empty when there is none. */
	public String text() {
		return textBody == null ? "" : textBody.trim();
	}

}
