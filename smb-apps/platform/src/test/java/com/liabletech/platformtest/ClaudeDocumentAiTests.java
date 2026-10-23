package com.liabletech.platformtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.liabletech.platform.ai.AiResult;
import com.liabletech.platform.ai.DocumentAi;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.samples.SampleArt;

/**
 * Runs the real Claude client against a local stand-in for the Messages API, checking the request
 * it sends (model, effort, JSON schema, fallback opt-in, the photo) and how it reads the reply.
 */
@SpringBootTest(classes = TestPlatformApplication.class)
@TestPropertySource(locations = "classpath:platform-test.properties",
		properties = { "app.ai.api-key=test-key", "app.ai.model=claude-opus-5-5", "app.ai.effort=medium" })
class ClaudeDocumentAiTests {

	private static final HttpServer SERVER;

	private static final List<Captured> CAPTURED = new ArrayList<>();

	private static final Deque<Reply> REPLIES = new ArrayDeque<>();

	static {
		try {
			SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		SERVER.createContext("/", (exchange) -> {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			synchronized (CAPTURED) {
				CAPTURED.add(new Captured(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("anthropic-beta"),
						exchange.getRequestHeaders().getFirst("Authorization"), body));
			}
			Reply reply = REPLIES.isEmpty() ? new Reply(500, "{}") : REPLIES.poll();
			byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(reply.status(), bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		SERVER.start();
	}

	@DynamicPropertySource
	static void apiBaseUrl(DynamicPropertyRegistry registry) {
		registry.add("app.ai.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
	}

	@AfterAll
	static void stop() {
		SERVER.stop(0);
	}

	@Autowired
	private DocumentAi ai;

	@Autowired
	private FileStore files;

	private final JsonMapper json = JsonMapper.builder().build();

	record PodReading(@JsonPropertyDescription("LR number") String lrNumber, String deliveryDate, boolean stampPresent,
			double confidence) {
	}

	@BeforeEach
	void clear() {
		CAPTURED.clear();
		REPLIES.clear();
	}

	@Test
	void sendsAPhotoWithSchemaAndReadsTheStructuredReply() {
		REPLIES.add(new Reply(200, message("end_turn",
				"{\\\"lrNumber\\\":\\\"4521\\\",\\\"deliveryDate\\\":\\\"2026-10-12\\\",\\\"stampPresent\\\":true,\\\"confidence\\\":0.93}")));
		StoredFile photo = files.save(null, SampleArt.paper(800, 600, 1).text(40, 80, 30, true, "LR 4521").jpeg(),
				"image/jpeg", "pod.jpg");

		AiResult<PodReading> result = ai.read(new DocumentAi.Request<>(null, "pod", "A proof of delivery.",
				List.of(photo), "caption: LR 4521", PodReading.class));

		assertThat(result.ok()).isTrue();
		assertThat(result.value()).isEqualTo(new PodReading("4521", "2026-10-12", true, 0.93));
		Captured request = CAPTURED.getFirst();
		assertThat(request.path()).isEqualTo("/v1/messages");
		assertThat(request.authorization()).isEqualTo("Bearer test-key");
		assertThat(request.beta()).contains("server-side-fallback-2026-07-01");
		JsonNode body = json.readTree(request.body());
		assertThat(body.path("model").asString()).isEqualTo("claude-opus-5-5");
		assertThat(body.path("fallbacks").asString()).isEqualTo("default");
		assertThat(body.has("temperature")).isFalse();
		assertThat(body.path("output_config").path("effort").asString()).isEqualTo("medium");
		JsonNode format = body.path("output_config").path("format");
		assertThat(format.path("type").asString()).isEqualTo("json_schema");
		assertThat(format.path("schema").path("properties").path("lrNumber").path("description").asString())
			.isEqualTo("LR number");
		assertThat(format.path("schema").path("additionalProperties").asBoolean()).isFalse();
		JsonNode blocks = body.path("messages").path(0).path("content");
		assertThat(blocks.path(0).path("type").asString()).isEqualTo("image");
		assertThat(blocks.path(0).path("source").path("media_type").asString()).isEqualTo("image/jpeg");
		assertThat(blocks.path(1).path("text").asString()).contains("caption: LR 4521");
	}

	@Test
	void reportsARefusalInsteadOfAReading() {
		REPLIES.add(new Reply(200, message("refusal", "")));

		AiResult<PodReading> result = ai.read(new DocumentAi.Request<>(null, "pod", "A proof of delivery.", List.of(),
				"LR 4521", PodReading.class));

		assertThat(result.outcome()).isEqualTo(AiResult.Outcome.REFUSED);
		assertThat(result.value()).isNull();
	}

	@Test
	void dropsTheFallbackOptionWhenTheApiRejectsIt() {
		REPLIES.add(new Reply(400, """
				{"type":"error","error":{"type":"invalid_request_error","message":"fallbacks: beta not enabled"}}"""));
		REPLIES.add(new Reply(200, message("end_turn",
				"{\\\"lrNumber\\\":\\\"77\\\",\\\"deliveryDate\\\":\\\"\\\",\\\"stampPresent\\\":false,\\\"confidence\\\":0.4}")));

		AiResult<PodReading> result = ai.read(new DocumentAi.Request<>(null, "pod", "A proof of delivery.", List.of(),
				"LR 77", PodReading.class));

		assertThat(result.ok()).isTrue();
		assertThat(result.value().lrNumber()).isEqualTo("77");
		assertThat(CAPTURED).hasSize(2);
		assertThat(json.readTree(CAPTURED.get(1).body()).has("fallbacks")).isFalse();
	}

	private static String message(String stopReason, String jsonText) {
		String content = jsonText.isEmpty() ? "[]" : "[{\"type\":\"text\",\"text\":\"" + jsonText + "\"}]";
		return """
				{"id":"msg_test","type":"message","role":"assistant","model":"claude-opus-5-5","content":%s,
				 "stop_reason":"%s","stop_sequence":null,"usage":{"input_tokens":1500,"output_tokens":120}}"""
			.formatted(content, stopReason);
	}

	record Captured(String path, String beta, String authorization, String body) {
	}

	record Reply(int status, String body) {
	}

}
