package com.liabletech.materials;

import java.time.LocalDate;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.liabletech.testing.MutableClock;
import com.liabletech.testing.StubDocumentAi;

/** A stub for Claude and a clock the tests can move: 1 October 2026 at noon IST. */
@TestConfiguration(proxyBeanMethods = false)
public class MaterialTestConfig {

	public static final LocalDate START = LocalDate.of(2026, 10, 1);

	@Bean
	@Primary
	StubDocumentAi stubDocumentAi() {
		return new StubDocumentAi();
	}

	@Bean
	@Primary
	MutableClock testClock() {
		return MutableClock.at(START);
	}

}
