package com.liabletech.parts;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.liabletech.parts.voice.SpeechToText;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.testing.MutableClock;
import com.liabletech.testing.StubDocumentAi;

/** Stubs for Claude and speech to text, and a clock the tests can move: 1 October 2026 at noon IST. */
@TestConfiguration(proxyBeanMethods = false)
public class PartsTestConfig {

	public static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

	@Bean
	@Primary
	StubDocumentAi stubDocumentAi() {
		return new StubDocumentAi();
	}

	@Bean
	@Primary
	MutableClock testClock() {
		return MutableClock.at(TODAY);
	}

	@Bean
	@Primary
	FakeSpeechToText fakeSpeechToText() {
		return new FakeSpeechToText();
	}

	/** Hears whatever the test says was spoken; off until a test sets the words. */
	public static class FakeSpeechToText implements SpeechToText {

		private String words;

		public void willHear(String words) {
			this.words = words;
		}

		@Override
		public boolean available() {
			return words != null;
		}

		@Override
		public Optional<String> transcribe(StoredFile audio, byte[] bytes) {
			return Optional.ofNullable(words);
		}

	}

}
