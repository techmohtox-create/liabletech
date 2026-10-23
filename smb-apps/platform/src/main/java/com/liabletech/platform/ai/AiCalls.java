package com.liabletech.platform.ai;

import java.time.OffsetDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** A log of model calls per account, so the cost of the AI step can be seen against the plan price. */
@Repository
public class AiCalls {

	private final JdbcClient jdbc;

	public AiCalls(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void record(Long accountId, String purpose, String model, AiResult.Outcome outcome, Long inputTokens,
			Long outputTokens, long durationMs, String error, OffsetDateTime at) {
		jdbc.sql("""
				INSERT INTO ai_call (account_id, purpose, model, outcome, input_tokens, output_tokens, duration_ms,
				    error, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
			.params(accountId, purpose, model, outcome.name(), inputTokens == null ? null : inputTokens.intValue(),
					outputTokens == null ? null : outputTokens.intValue(), (int) durationMs,
					error == null || error.length() <= 1000 ? error : error.substring(0, 1000), at)
			.update();
	}

	public Usage usageSince(long accountId, OffsetDateTime since) {
		return jdbc.sql("""
				SELECT COUNT(*) AS calls, COALESCE(SUM(input_tokens), 0) AS input_tokens,
				    COALESCE(SUM(output_tokens), 0) AS output_tokens
				FROM ai_call WHERE account_id = ? AND created_at >= ?
				""").params(accountId, since).query(Usage.class).single();
	}

	public record Usage(long calls, long inputTokens, long outputTokens) {
	}

}
