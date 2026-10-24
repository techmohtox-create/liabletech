package com.liabletech.parts.voice;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

import com.liabletech.platform.files.StoredFile;

/**
 * Speech to text over HTTP, for any service that offers the widely copied
 * {@code POST /v1/audio/transcriptions} form (a self-hosted Whisper server, for one). Set
 * {@code app.stt.url} to the full endpoint URL; with no URL, voice notes are not transcribed.
 */
@Component
public class HttpSpeechToText implements SpeechToText {

	private static final Logger log = LoggerFactory.getLogger(HttpSpeechToText.class);

	private final String url;

	private final String apiKey;

	private final String model;

	private final String language;

	private final RestClient rest;

	public HttpSpeechToText(@Value("${app.stt.url:}") String url, @Value("${app.stt.api-key:}") String apiKey,
			@Value("${app.stt.model:whisper-1}") String model, @Value("${app.stt.language:}") String language) {
		this.url = url;
		this.apiKey = apiKey;
		this.model = model;
		this.language = language;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(10));
		factory.setReadTimeout(Duration.ofSeconds(60));
		this.rest = RestClient.builder().requestFactory(factory).build();
	}

	@Override
	public boolean available() {
		return StringUtils.hasText(url);
	}

	@Override
	public Optional<String> transcribe(StoredFile audio, byte[] bytes) {
		if (!available()) {
			return Optional.empty();
		}
		HttpHeaders partHeaders = new HttpHeaders();
		partHeaders.setContentType(MediaType.parseMediaType(audio.contentType()));
		String filename = audio.originalName() != null ? audio.originalName() : "voice-note";
		ByteArrayResource resource = new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return filename;
			}
		};
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("file", new HttpEntity<>(resource, partHeaders));
		form.add("model", model);
		if (StringUtils.hasText(language)) {
			form.add("language", language);
		}
		try {
			RestClient.RequestBodySpec request = rest.post().uri(url).contentType(MediaType.MULTIPART_FORM_DATA);
			if (StringUtils.hasText(apiKey)) {
				request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
			}
			JsonNode response = request.body(form).retrieve().body(JsonNode.class);
			String text = response == null ? null : response.path("text").asString(null);
			return StringUtils.hasText(text) ? Optional.of(text.trim()) : Optional.empty();
		}
		catch (RestClientException ex) {
			log.warn("Voice note could not be transcribed: {}", ex.getMessage());
			return Optional.empty();
		}
	}

}
