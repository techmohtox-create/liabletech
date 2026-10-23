package com.liabletech.platform.numbering;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class DocumentNumbersTests {

	@Test
	void financialYearRunsAprilToMarch() {
		assertThat(DocumentNumbers.financialYear(LocalDate.of(2026, 4, 1))).isEqualTo("2026-27");
		assertThat(DocumentNumbers.financialYear(LocalDate.of(2027, 3, 31))).isEqualTo("2026-27");
		assertThat(DocumentNumbers.financialYear(LocalDate.of(2026, 3, 31))).isEqualTo("2025-26");
		assertThat(DocumentNumbers.financialYear(LocalDate.of(2099, 12, 1))).isEqualTo("2099-00");
	}

}
