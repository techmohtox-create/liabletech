package com.liabletech.platform.whatsapp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.messaging.Outgoing;
import com.liabletech.platform.text.Phones;

/**
 * Sends a WhatsApp message through the Cloud API, or only pretends to in simulator mode. Callers
 * use {@link com.liabletech.platform.messaging.Messenger}, which also records the outbox.
 */
@Component
public class WhatsAppGateway {

	private static final Logger log = LoggerFactory.getLogger(WhatsAppGateway.class);

	/** WhatsApp's limit for a document or image caption. */
	private static final int MAX_CAPTION = 1024;

	private final WhatsAppProperties properties;

	private final CloudApi cloudApi;

	private final FileStore files;

	public WhatsAppGateway(WhatsAppProperties properties, CloudApi cloudApi, FileStore files) {
		this.properties = properties;
		this.cloudApi = cloudApi;
		this.files = files;
	}

	public Result send(Outgoing message, String fromPhoneNumberId) {
		if (properties.simulator()) {
			return new Result(true, true, "sim-" + UUID.randomUUID(), null);
		}
		if (!properties.cloudReady()) {
			return new Result(false, false, null, "WhatsApp Cloud API is not configured (WHATSAPP_ACCESS_TOKEN)");
		}
		String numberId = StringUtils.hasText(fromPhoneNumberId) ? fromPhoneNumberId : properties.phoneNumberId();
		try {
			List<Map<String, Object>> payloads = payloads(message, numberId);
			String firstId = null;
			for (Map<String, Object> payload : payloads) {
				String id = cloudApi.send(numberId, payload);
				firstId = firstId == null ? id : firstId;
			}
			return new Result(true, false, firstId, null);
		}
		catch (RestClientResponseException ex) {
			log.warn("WhatsApp send to {} failed: {} {}", message.to(), ex.getStatusCode(), ex.getResponseBodyAsString());
			return new Result(false, false, null, ex.getStatusCode() + " " + ex.getResponseBodyAsString());
		}
		catch (RuntimeException ex) {
			log.warn("WhatsApp send to {} failed", message.to(), ex);
			return new Result(false, false, null, ex.getMessage());
		}
	}

	/**
	 * One or more API payloads. With templates on, a single template message (first attachment as
	 * its document header). Otherwise the text, then each attachment, using the text as the first
	 * attachment's caption when it fits.
	 */
	private List<Map<String, Object>> payloads(Outgoing message, String numberId) {
		String to = Phones.waId(message.to());
		List<Map<String, Object>> payloads = new ArrayList<>();
		if (properties.useTemplates() && message.template() != null) {
			Map<String, Object> template = new LinkedHashMap<>();
			template.put("name", message.template().name());
			template.put("language", Map.of("code", properties.templateLanguage()));
			List<Map<String, Object>> components = new ArrayList<>();
			if (!message.attachments().isEmpty()) {
				Outgoing.Attachment first = message.attachments().getFirst();
				components.add(Map.of("type", "header", "parameters",
						List.of(Map.of("type", "document", "document",
								Map.of("id", upload(numberId, first), "filename", first.filename())))));
			}
			List<Map<String, Object>> parameters = message.template()
				.bodyParameters()
				.stream()
				.map((value) -> Map.<String, Object>of("type", "text", "text", value))
				.toList();
			components.add(Map.of("type", "body", "parameters", parameters));
			template.put("components", components);
			payloads.add(base(to, "template", template));
			return payloads;
		}
		String text = message.body();
		boolean captionUsed = false;
		if (message.attachments().isEmpty() || text.length() > MAX_CAPTION) {
			payloads.add(base(to, "text", Map.of("preview_url", true, "body", text)));
			captionUsed = true;
		}
		for (Outgoing.Attachment attachment : message.attachments()) {
			StoredFile file = files.get(attachment.fileId());
			Map<String, Object> media = new LinkedHashMap<>();
			media.put("id", upload(numberId, attachment));
			if (!captionUsed) {
				media.put("caption", text);
				captionUsed = true;
			}
			if (file.isImage()) {
				payloads.add(base(to, "image", media));
			}
			else {
				media.put("filename", attachment.filename());
				payloads.add(base(to, "document", media));
			}
		}
		return payloads;
	}

	private String upload(String numberId, Outgoing.Attachment attachment) {
		StoredFile file = files.get(attachment.fileId());
		return cloudApi.upload(numberId, files.read(file), file.contentType(), attachment.filename());
	}

	private static Map<String, Object> base(String to, String type, Map<String, Object> content) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("messaging_product", "whatsapp");
		payload.put("recipient_type", "individual");
		payload.put("to", to);
		payload.put("type", type);
		payload.put(type, content);
		return payload;
	}

	public record Result(boolean ok, boolean simulated, String providerMessageId, String error) {
	}

}
