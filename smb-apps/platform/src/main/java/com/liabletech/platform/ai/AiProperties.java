package com.liabletech.platform.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Settings for reading documents with Claude.
 *
 * @param apiKey the Anthropic API key ({@code ANTHROPIC_API_KEY}); without one, AI reading is off
 * @param model the Claude model id
 * @param effort how hard the model thinks: {@code low}, {@code medium}, {@code high}...
 * @param maxTokens output ceiling per call, thinking included
 * @param baseUrl optional API base URL (a gateway, or a local stub in tests)
 * @param fallbacks retry declined requests on the model Anthropic recommends (server-side fallback)
 */
@ConfigurationProperties("app.ai")
public record AiProperties(String apiKey, String model, String effort, long maxTokens, Duration timeout,
		String baseUrl, boolean fallbacks) {

	public boolean enabled() {
		return StringUtils.hasText(apiKey);
	}

}
