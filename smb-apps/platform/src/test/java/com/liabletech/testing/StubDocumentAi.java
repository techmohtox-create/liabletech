package com.liabletech.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.liabletech.platform.ai.AiResult;
import com.liabletech.platform.ai.DocumentAi;

/**
 * A {@link DocumentAi} for tests: returns whatever reading a test sets up for a result type, and
 * remembers the requests it was given. Register it as a {@code @Primary} bean in a test
 * configuration.
 */
public class StubDocumentAi implements DocumentAi {

	private final Map<Class<?>, AiResult<?>> results = new HashMap<>();

	private final List<Request<?>> requests = new ArrayList<>();

	public <T> StubDocumentAi willRead(Class<T> type, T value) {
		results.put(type, AiResult.ok(value));
		return this;
	}

	public <T> StubDocumentAi willFail(Class<T> type, AiResult.Outcome outcome) {
		results.put(type, AiResult.failed(outcome, "stubbed " + outcome));
		return this;
	}

	public void reset() {
		results.clear();
		requests.clear();
	}

	public List<Request<?>> requests() {
		return requests;
	}

	@Override
	public boolean available() {
		return true;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> AiResult<T> read(Request<T> request) {
		requests.add(request);
		AiResult<?> result = results.get(request.resultType());
		return result == null ? AiResult.failed(AiResult.Outcome.FAILED, "no stubbed reading")
				: (AiResult<T>) result;
	}

}
