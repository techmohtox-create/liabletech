package com.liabletech.platform.whatsapp;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

import com.liabletech.platform.text.Phones;

/**
 * Reads the JSON that the WhatsApp Cloud API posts to the webhook: new messages, and delivery
 * statuses for messages we sent.
 */
public record WebhookPayload(List<Message> messages, List<Status> statuses) {

	public record Message(String waMessageId, String from, String phoneNumberId, String contactName, String type,
			String text, String mediaId, String mimeType, String filename, String contextId, OffsetDateTime timestamp) {
	}

	public record Status(String waMessageId, String status, String error, OffsetDateTime timestamp) {
	}

	public static WebhookPayload parse(JsonNode root) {
		List<Message> messages = new ArrayList<>();
		List<Status> statuses = new ArrayList<>();
		for (JsonNode entry : root.path("entry")) {
			for (JsonNode change : entry.path("changes")) {
				JsonNode value = change.path("value");
				String phoneNumberId = value.path("metadata").path("phone_number_id").asString(null);
				for (JsonNode message : value.path("messages")) {
					String from = message.path("from").asString(null);
					messages.add(message(message, phoneNumberId, contactName(value, from)));
				}
				for (JsonNode status : value.path("statuses")) {
					JsonNode firstError = status.path("errors").path(0);
					String error = firstError.isMissingNode() ? null
							: firstError.path("code").asString("") + " " + firstError.path("title").asString("");
					statuses.add(new Status(status.path("id").asString(null), status.path("status").asString(""),
							error, timestamp(status)));
				}
			}
		}
		return new WebhookPayload(messages, statuses);
	}

	private static Message message(JsonNode message, String phoneNumberId, String contactName) {
		String type = message.path("type").asString("unknown");
		String text = null;
		String mediaId = null;
		String mimeType = null;
		String filename = null;
		switch (type) {
			case "text" -> text = message.path("text").path("body").asString(null);
			case "image", "document", "audio", "video", "sticker" -> {
				JsonNode media = message.path(type);
				mediaId = media.path("id").asString(null);
				mimeType = media.path("mime_type").asString(null);
				filename = media.path("filename").asString(null);
				text = media.path("caption").asString(null);
			}
			case "button" -> text = message.path("button").path("text").asString(null);
			case "interactive" -> {
				JsonNode interactive = message.path("interactive");
				JsonNode reply = interactive.has("button_reply") ? interactive.path("button_reply")
						: interactive.path("list_reply");
				text = reply.path("title").asString(null);
			}
			case "location" -> {
				JsonNode location = message.path("location");
				text = location.path("latitude").asString("") + "," + location.path("longitude").asString("");
			}
			default -> {
				// reactions, contacts and anything new are stored with their type only
			}
		}
		return new Message(message.path("id").asString(null), Phones.normalize(message.path("from").asString(null)),
				phoneNumberId, contactName, type, text, mediaId, mimeType, filename,
				message.path("context").path("id").asString(null), timestamp(message));
	}

	private static String contactName(JsonNode value, String from) {
		for (JsonNode contact : value.path("contacts")) {
			if (from == null || from.equals(contact.path("wa_id").asString(null))) {
				return contact.path("profile").path("name").asString(null);
			}
		}
		return null;
	}

	private static OffsetDateTime timestamp(JsonNode node) {
		long seconds = node.path("timestamp").asLong(0);
		return seconds == 0 ? null : OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneOffset.UTC);
	}

}
