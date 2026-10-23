package com.liabletech.platform.ai;

/** What came back from reading a document, and whether it can be used. */
public record AiResult<T>(Outcome outcome, T value, String message) {

	public enum Outcome {

		/** Read successfully; {@code value} is set. */
		OK,
		/** No API key: the document needs manual entry. */
		DISABLED,
		/** The model declined to read it. */
		REFUSED,
		/** The call failed (network, rate limit, unreadable reply). */
		FAILED

	}

	public boolean ok() {
		return outcome == Outcome.OK && value != null;
	}

	public static <T> AiResult<T> ok(T value) {
		return new AiResult<>(Outcome.OK, value, null);
	}

	public static <T> AiResult<T> disabled() {
		return new AiResult<>(Outcome.DISABLED, null, "AI reading is off (no ANTHROPIC_API_KEY); enter the details by hand.");
	}

	public static <T> AiResult<T> failed(Outcome outcome, String message) {
		return new AiResult<>(outcome, null, message);
	}

}
