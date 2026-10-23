package com.liabletech.payoutcheck.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.liabletech.payoutcheck.common.BadRequestException;
import com.liabletech.payoutcheck.common.Inr;
import com.liabletech.payoutcheck.common.Money;

class ParsingTests {

	@Test
	void parsesAmountsAsTheyAppearInExports() {
		assertThat(Values.money("₹1,234.50")).isEqualByComparingTo("1234.50");
		assertThat(Values.money("Rs. 99")).isEqualByComparingTo("99.00");
		assertThat(Values.money("(120.00)")).isEqualByComparingTo("-120.00");
		assertThat(Values.money("-45.255")).isEqualByComparingTo("-45.26");
		assertThat(Values.money("")).isEqualByComparingTo("0");
		assertThat(Values.money("-")).isEqualByComparingTo("0");
		assertThat(Values.money("NA")).isEqualByComparingTo("0");
		assertThat(Values.deduction("-77.40")).isEqualByComparingTo("77.40");
		assertThatThrownBy(() -> Values.money("abc")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void parsesIndianDateFormats() {
		assertThat(Values.date("13/10/2026")).isEqualTo(LocalDate.of(2026, 10, 13));
		assertThat(Values.date("13-10-2026")).isEqualTo(LocalDate.of(2026, 10, 13));
		assertThat(Values.date("13-Oct-2026")).isEqualTo(LocalDate.of(2026, 10, 13));
		assertThat(Values.date("2026-10-13T00:00:00")).isEqualTo(LocalDate.of(2026, 10, 13));
		assertThat(Values.dateTime("13/10/2026 07:45 PM")).isEqualTo(LocalDateTime.of(2026, 10, 13, 19, 45));
		assertThat(Values.dateTime("2026-10-13 13:05")).isEqualTo(LocalDateTime.of(2026, 10, 13, 13, 5));
		assertThat(Values.dateTime("13 Oct 2026")).isEqualTo(LocalDateTime.of(2026, 10, 13, 12, 0));
		assertThatThrownBy(() -> Values.date("someday")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void mapsStatusWording() {
		assertThat(OrderStatus.parse("Delivered")).isEqualTo(OrderStatus.DELIVERED);
		assertThat(OrderStatus.parse("COMPLETED")).isEqualTo(OrderStatus.DELIVERED);
		assertThat(OrderStatus.parse("Cancelled by customer")).isEqualTo(OrderStatus.CANCELLED);
		assertThat(OrderStatus.parse("Restaurant rejected")).isEqualTo(OrderStatus.REJECTED);
		assertThat(OrderStatus.parse("")).isEqualTo(OrderStatus.DELIVERED);
	}

	@Test
	void findsTheHeaderRowBelowTitleRows() {
		List<List<String>> rows = TabularReader.readCsv("""
				Swiggy Payout Annexure
				Restaurant: Spice Route,,,,
				Order No,Order Date,Item Total,Commission,Taxes on Service & Payment Collection Charges,Payment Collection Charges,TDS u/s 194O,Net Payable
				SWG1,2026-10-13,450,77.40,15.48,8.60,0.43,348.09
				""".getBytes(StandardCharsets.UTF_8));
		HeaderMatch match = HeaderMatch.find(rows, Formats.STATEMENT);
		assertThat(match.headerRow()).isEqualTo(2);
		List<String> data = rows.get(3);
		assertThat(match.value(data, "orderRef")).isEqualTo("SWG1");
		assertThat(match.value(data, "commission")).isEqualTo("77.40");
		assertThat(match.value(data, "gstOnFees")).isEqualTo("15.48");
		assertThat(match.value(data, "pgFee")).isEqualTo("8.60");
		assertThat(match.value(data, "tds")).isEqualTo("0.43");
		assertThat(match.value(data, "netPayout")).isEqualTo("348.09");
	}

	@Test
	void reportsMissingRequiredColumns() {
		List<List<String>> rows = TabularReader.readCsv("Order ID,Amount\nA1,100\n".getBytes(StandardCharsets.UTF_8));
		assertThatThrownBy(() -> HeaderMatch.find(rows, Formats.ORDERS)).isInstanceOf(BadRequestException.class)
			.hasMessageContaining("Order date")
			.hasMessageContaining("Item total");
	}

	@Test
	void readsSemicolonSeparatedFilesWithABom() {
		List<List<String>> rows = TabularReader
			.readCsv("﻿Order ID;Order Date;Item Total\nA1;13/10/2026;250\n".getBytes(StandardCharsets.UTF_8));
		assertThat(rows.get(0)).containsExactly("Order ID", "Order Date", "Item Total");
		assertThat(rows.get(1)).containsExactly("A1", "13/10/2026", "250");
	}

	@Test
	void readsExcelWorkbooksPickingTheBiggestSheet() throws IOException {
		byte[] content;
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			workbook.createSheet("Summary").createRow(0).createCell(0).setCellValue("Total payout");
			Sheet sheet = workbook.createSheet("Orders");
			Row header = sheet.createRow(0);
			header.createCell(0).setCellValue("Order ID");
			header.createCell(1).setCellValue("Item Total");
			Row data = sheet.createRow(1);
			data.createCell(0).setCellValue(123456789);
			data.createCell(1).setCellValue(250.5);
			workbook.write(out);
			content = out.toByteArray();
		}
		List<List<String>> rows = TabularReader.read("annexure.xlsx", content);
		assertThat(rows).hasSize(2);
		assertThat(rows.get(1)).containsExactly("123456789", "250.5");
	}

	@Test
	void formatsRupeesWithIndianGrouping() {
		assertThat(Inr.format(Money.of("1234567.5"))).isEqualTo("₹12,34,567.50");
		assertThat(Inr.format(Money.of("999"))).isEqualTo("₹999.00");
		assertThat(Inr.format(Money.of("-1500"))).isEqualTo("-₹1,500.00");
	}

}
