package com.liabletech.platform.whatsapp;

import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/**
 * The few WhatsApp Business Cloud API (Graph API) calls the apps need: download a received photo,
 * upload a PDF, and send a message.
 */
@Component
public class CloudApi {

	private final WhatsAppProperties properties;

	private final RestClient rest;

	public CloudApi(WhatsAppProperties properties) {
		this.properties = properties;
		this.rest = RestClient.builder().baseUrl("https://graph.facebook.com/" + properties.graphVersion()).build();
	}

	/** Downloads received media in two steps: look up its temporary URL, then fetch it with the token. */
	public Media download(String mediaId) {
		JsonNode info = rest.get().uri("/{id}", mediaId).header(HttpHeaders.AUTHORIZATION, bearer()).retrieve().body(JsonNode.class);
		String url = info.path("url").asString();
		byte[] bytes = RestClient.create().get().uri(url).header(HttpHeaders.AUTHORIZATION, bearer()).retrieve().body(byte[].class);
		return new Media(bytes, info.path("mime_type").asString(null));
	}

	/** Uploads a file for sending and returns its media id. */
	public String upload(String phoneNumberId, byte[] bytes, String contentType, String filename) {
		HttpHeaders partHeaders = new HttpHeaders();
		partHeaders.setContentType(MediaType.parseMediaType(contentType));
		ByteArrayResource resource = new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return filename;
			}
		};
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("messaging_product", "whatsapp");
		form.add("type", contentType);
		form.add("file", new HttpEntity<>(resource, partHeaders));
		JsonNode response = rest.post()
			.uri("/{phoneNumberId}/media", phoneNumberId)
			.header(HttpHeaders.AUTHORIZATION, bearer())
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(form)
			.retrieve()
			.body(JsonNode.class);
		return response.path("id").asString();
	}

	/** Sends one message payload (text, document, image or template) and returns WhatsApp's message id. */
	public String send(String phoneNumberId, Map<String, Object> payload) {
		JsonNode response = rest.post()
			.uri("/{phoneNumberId}/messages", phoneNumberId)
			.header(HttpHeaders.AUTHORIZATION, bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.body(payload)
			.retrieve()
			.body(JsonNode.class);
		return response.path("messages").path(0).path("id").asString(null);
	}

	private String bearer() {
		return "Bearer " + properties.accessToken();
	}

	public record Media(byte[] bytes, String mimeType) {
	}

}
