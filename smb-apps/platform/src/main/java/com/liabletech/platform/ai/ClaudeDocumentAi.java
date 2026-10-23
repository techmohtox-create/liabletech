package com.liabletech.platform.ai;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.Images;
import com.liabletech.platform.files.StoredFile;

/**
 * Reads documents with Claude, using structured output so the reply always matches the result
 * record's JSON schema.
 *
 * <p>
 * Requests opt in to server-side fallback: if the model declines a request, the API retries it on
 * the model Anthropic recommends instead of failing. If an API key's organisation does not have
 * that beta, the first 400 that names it switches the option off for the rest of the run.
 */
@Service
public class ClaudeDocumentAi implements DocumentAi {

	private static final Logger log = LoggerFactory.getLogger(ClaudeDocumentAi.class);

	private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

	/** Long edge for photos sent to the model: small print on a receipt stays legible. */
	private static final int MAX_IMAGE_EDGE = 2000;

	private static final String SYSTEM_PROMPT = """
			You read business documents that small businesses in India photograph or scan: lorry receipts and \
			proof-of-delivery copies, delivery challans, tax invoices, letters of credit, packing lists, bills of \
			lading, appliance rating labels and spare parts.

			Rules for every field:
			- Report only what is written or clearly visible. Never guess or invent a value.
			- When a value is missing, unreadable or does not apply, use an empty string "" (false, 0 or an empty \
			list when the field's type needs one).
			- Dates as YYYY-MM-DD. Indian documents put the day first: 12/10/26 is 12 October 2026.
			- Amounts and quantities in text fields as plain digits with a dot for decimals: "123456.50", not \
			"1,23,456.50/-" and no currency symbols, unless the field says otherwise.
			- Copy reference numbers exactly as printed, with their letters, slashes and dashes.
			- Write names in English letters; transliterate Hindi or other Indian-language text and translate \
			handwritten remarks into English.
			- confidence is your overall confidence, from 0 to 1, that the fields are right. Lower it for a \
			blurred, cut-off, dark or handwritten document.""";

	private final AiProperties properties;

	private final FileStore files;

	private final ObjectMapper json;

	private final AiCalls calls;

	private final Clock clock;

	private volatile AnthropicClient client;

	private volatile boolean fallbacks;

	public ClaudeDocumentAi(AiProperties properties, FileStore files, ObjectMapper json, AiCalls calls, Clock clock) {
		this.properties = properties;
		this.files = files;
		this.json = json;
		this.calls = calls;
		this.clock = clock;
		this.fallbacks = properties.fallbacks();
	}

	@Override
	public boolean available() {
		return properties.enabled();
	}

	@Override
	public <T> AiResult<T> read(Request<T> request) {
		if (!available()) {
			return AiResult.disabled();
		}
		long started = System.nanoTime();
		Long inputTokens = null;
		Long outputTokens = null;
		try {
			List<ContentBlockParam> content = content(request);
			Message response;
			try {
				response = client().messages().create(params(request, content, fallbacks));
			}
			catch (BadRequestException ex) {
				if (!fallbacks || !String.valueOf(ex.getMessage()).toLowerCase(java.util.Locale.ROOT).contains("fallback")) {
					throw ex;
				}
				log.warn("Server-side fallback is not available for this API key; continuing without it");
				fallbacks = false;
				response = client().messages().create(params(request, content, false));
			}
			inputTokens = response.usage().inputTokens();
			outputTokens = response.usage().outputTokens();
			StopReason stop = response.stopReason().orElse(null);
			if (StopReason.REFUSAL.equals(stop)) {
				return finish(request, AiResult.failed(AiResult.Outcome.REFUSED, "The model declined to read this document."),
						started, inputTokens, outputTokens);
			}
			if (StopReason.MAX_TOKENS.equals(stop)) {
				return finish(request, AiResult.failed(AiResult.Outcome.FAILED, "The reading was cut off; raise app.ai.max-tokens."),
						started, inputTokens, outputTokens);
			}
			String text = response.content()
				.stream()
				.flatMap((block) -> block.text().stream())
				.map(TextBlock::text)
				.collect(Collectors.joining());
			T value = json.readValue(text, request.resultType());
			return finish(request, AiResult.ok(value), started, inputTokens, outputTokens);
		}
		catch (AnthropicServiceException ex) {
			log.warn("Claude call for {} failed with HTTP {}: {}", request.purpose(), ex.statusCode(), ex.getMessage());
			return finish(request, AiResult.failed(AiResult.Outcome.FAILED, "AI service error " + ex.statusCode()), started,
					inputTokens, outputTokens);
		}
		catch (AnthropicException | JacksonException ex) {
			log.warn("Claude call for {} failed", request.purpose(), ex);
			return finish(request, AiResult.failed(AiResult.Outcome.FAILED, "Could not read the document: " + ex.getMessage()),
					started, inputTokens, outputTokens);
		}
	}

	private MessageCreateParams params(Request<?> request, List<ContentBlockParam> content, boolean withFallbacks) {
		JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
		for (Map.Entry<String, Object> entry : JsonSchemas.of(request.resultType()).entrySet()) {
			schema.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
		}
		MessageCreateParams.Builder builder = MessageCreateParams.builder()
			.model(properties.model())
			.maxTokens(properties.maxTokens())
			.system(SYSTEM_PROMPT + "\n\nThis document:\n" + request.instructions())
			.outputConfig(OutputConfig.builder()
				.effort(OutputConfig.Effort.of(properties.effort()))
				.format(JsonOutputFormat.builder().schema(schema.build()).build())
				.build())
			.addUserMessageOfContentBlockParams(content);
		if (withFallbacks) {
			builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
				.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
		}
		return builder.build();
	}

	private List<ContentBlockParam> content(Request<?> request) {
		List<ContentBlockParam> blocks = new ArrayList<>();
		for (StoredFile file : request.files()) {
			byte[] bytes = files.read(file);
			if (file.isPdf()) {
				blocks.add(ContentBlockParam
					.ofDocument(Base64PdfSource.builder().data(Base64.getEncoder().encodeToString(bytes)).build()));
			}
			else if (file.isImage()) {
				Images.Prepared image = Images.fit(bytes, file.contentType(), MAX_IMAGE_EDGE);
				blocks.add(ContentBlockParam.ofImage(Base64ImageSource.builder()
					.data(Base64.getEncoder().encodeToString(image.bytes()))
					.mediaType(Base64ImageSource.MediaType.of(image.contentType()))
					.build()));
			}
		}
		if (StringUtils.hasText(request.text())) {
			blocks.add(ContentBlockParam.ofText("Text sent with the document:\n" + request.text().trim()));
		}
		blocks.add(ContentBlockParam.ofText(request.files().isEmpty() ? "Read the text above into the requested fields."
				: "Read the document above into the requested fields."));
		return blocks;
	}

	private <T> AiResult<T> finish(Request<T> request, AiResult<T> result, long started, Long inputTokens,
			Long outputTokens) {
		long millis = (System.nanoTime() - started) / 1_000_000;
		calls.record(request.accountId(), request.purpose(), properties.model(), result.outcome(), inputTokens,
				outputTokens, millis, result.message(), OffsetDateTime.now(clock));
		return result;
	}

	private AnthropicClient client() {
		AnthropicClient existing = client;
		if (existing != null) {
			return existing;
		}
		synchronized (this) {
			if (client == null) {
				AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
					.apiKey(properties.apiKey())
					.timeout(properties.timeout())
					.maxRetries(2);
				if (StringUtils.hasText(properties.baseUrl())) {
					builder.baseUrl(properties.baseUrl());
				}
				client = builder.build();
			}
			return client;
		}
	}

}
