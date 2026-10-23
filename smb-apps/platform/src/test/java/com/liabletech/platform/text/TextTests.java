package com.liabletech.platform.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TextTests {

	@Test
	void normalisesIndianMobileNumbers() {
		assertThat(Phones.normalize("98765 43210")).isEqualTo("+919876543210");
		assertThat(Phones.normalize("098765-43210")).isEqualTo("+919876543210");
		assertThat(Phones.normalize("+91 98765 43210")).isEqualTo("+919876543210");
		assertThat(Phones.normalize("919876543210")).isEqualTo("+919876543210");
		assertThat(Phones.normalize("12345")).isNull();
		assertThat(Phones.waId("+919876543210")).isEqualTo("919876543210");
		assertThat(Phones.pretty("+919876543210")).isEqualTo("+91 98765 43210");
	}

	@Test
	void findsAMobileNumberInsideACaption() {
		assertThat(Phones.findIn("cust ravi 98765 43210 bedroom AC")).isEqualTo("+919876543210");
		assertThat(Phones.findIn("LR 4521 dated 12/10")).isNull();
		assertThat(Phones.findIn("invoice 798765432101")).isNull();
	}

	@Test
	void formatsRupeesWithIndianGrouping() {
		assertThat(IndianMoney.group(new BigDecimal("12345678.5"), 2)).isEqualTo("1,23,45,678.50");
		assertThat(IndianMoney.group(new BigDecimal("999"), 0)).isEqualTo("999");
		assertThat(IndianMoney.group(new BigDecimal("100000"), 0)).isEqualTo("1,00,000");
		assertThat(IndianMoney.rupees(new BigDecimal("42500.00"))).isEqualTo("₹42,500");
		assertThat(IndianMoney.rupees(new BigDecimal("42500.5"))).isEqualTo("₹42,500.50");
	}

	@Test
	void writesAmountsInWordsTheIndianWay() {
		assertThat(IndianMoney.inWords(new BigDecimal("123456"))).isEqualTo(
				"Rupees One Lakh Twenty Three Thousand Four Hundred Fifty Six Only");
		assertThat(IndianMoney.inWords(new BigDecimal("25000000.50"))).isEqualTo(
				"Rupees Two Crore Fifty Lakh and Fifty Paise Only");
		assertThat(IndianMoney.inWords(new BigDecimal("0"))).isEqualTo("Rupees Zero Only");
	}

	@Test
	void parsesNumbersAsWrittenOnIndianDocuments() {
		assertThat(Lenient.number("1,23,456.50/-")).isEqualByComparingTo("123456.50");
		assertThat(Lenient.number("Rs. 2,500")).isEqualByComparingTo("2500");
		assertThat(Lenient.number("₹ 999")).isEqualByComparingTo("999");
		assertThat(Lenient.number("12.5 MT")).isEqualByComparingTo("12.5");
		assertThat(Lenient.number("Qty - 200 bags")).isEqualByComparingTo("200");
		assertThat(Lenient.number("nil")).isNull();
	}

	@Test
	void parsesDatesDayFirst() {
		LocalDate expected = LocalDate.of(2026, 10, 12);
		assertThat(Lenient.date("2026-10-12")).isEqualTo(expected);
		assertThat(Lenient.date("12/10/2026")).isEqualTo(expected);
		assertThat(Lenient.date("12-10-26")).isEqualTo(expected);
		assertThat(Lenient.date("12.10.2026")).isEqualTo(expected);
		assertThat(Lenient.date("12 Oct 2026")).isEqualTo(expected);
		assertThat(Lenient.date("12-OCT-2026")).isEqualTo(expected);
		assertThat(Lenient.date("12th October 2026")).isEqualTo(expected);
		assertThat(Lenient.date("Oct 12, 2026")).isEqualTo(expected);
		assertThat(Lenient.date("someday")).isNull();
		assertThat(Lenient.text("  N/A ")).isNull();
		assertThat(Lenient.text(" Pune ")).isEqualTo("Pune");
	}

	@Test
	void comparesNamesReadOffPhotos() {
		assertThat(Fuzzy.similarity("M/s. Sharma Roadlines Pvt. Ltd.", "SHARMA ROADLINES PRIVATE LIMITED"))
			.isEqualTo(1.0);
		assertThat(Fuzzy.similarity("Sharma Roadlines", "Sharna Roadlines")).isGreaterThan(0.85);
		assertThat(Fuzzy.similarity("Sharma Roadlines", "Gupta Cement Agency")).isLessThan(0.4);
		assertThat(Fuzzy.sameCode("LR-04521", "4521")).isTrue();
		assertThat(Fuzzy.sameCode("LR/4521", "lr 4521")).isTrue();
		assertThat(Fuzzy.sameCode("4521", "4512")).isFalse();
		assertThat(Fuzzy.coverage("FRESH RED ONIONS 25 KG BAGS", "Fresh red onions packed in 25 kg jute bags"))
			.isEqualTo(1.0);
	}

}
