package com.liabletech.payoutcheck.ingest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.liabletech.payoutcheck.common.BadRequestException;

/**
 * Turns an uploaded CSV or Excel file into rows of strings. Excel dates come out as ISO date-times
 * and numbers as plain decimals, so the rest of the import only ever parses text.
 */
public final class TabularReader {

	static final int MAX_ROWS = 50_000;

	private TabularReader() {
	}

	public static List<List<String>> read(String fileName, byte[] content) {
		String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
			return readExcel(content);
		}
		if (lower.endsWith(".csv") || lower.endsWith(".txt") || lower.isEmpty()) {
			return readCsv(content);
		}
		throw new BadRequestException("Upload a .csv, .xlsx or .xls file");
	}

	static List<List<String>> readCsv(byte[] content) {
		String text = new String(content, StandardCharsets.UTF_8);
		if (text.startsWith("﻿")) {
			text = text.substring(1);
		}
		char delimiter = detectDelimiter(text);
		CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setIgnoreEmptyLines(true).setTrim(true).get();
		List<List<String>> rows = new ArrayList<>();
		try (Reader reader = new java.io.StringReader(text); CSVParser parser = CSVParser.parse(reader, format)) {
			for (CSVRecord record : parser) {
				if (rows.size() >= MAX_ROWS) {
					throw new BadRequestException("File has more than " + MAX_ROWS + " rows");
				}
				rows.add(record.toList());
			}
		}
		catch (IOException | java.io.UncheckedIOException ex) {
			throw new BadRequestException("Could not read the CSV file: " + ex.getMessage());
		}
		return rows;
	}

	private static char detectDelimiter(String text) {
		int newline = text.indexOf('\n');
		String firstLines = text.substring(0, Math.min(text.length(), newline < 0 ? text.length() : Math.max(newline, 2000)));
		long commas = firstLines.chars().filter((c) -> c == ',').count();
		long semicolons = firstLines.chars().filter((c) -> c == ';').count();
		long tabs = firstLines.chars().filter((c) -> c == '\t').count();
		if (tabs > commas && tabs > semicolons) {
			return '\t';
		}
		return semicolons > commas ? ';' : ',';
	}

	static List<List<String>> readExcel(byte[] content) {
		DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
		List<List<String>> rows = new ArrayList<>();
		try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
			Sheet sheet = pickSheet(workbook);
			for (Row row : sheet) {
				if (rows.size() >= MAX_ROWS) {
					throw new BadRequestException("File has more than " + MAX_ROWS + " rows");
				}
				List<String> values = new ArrayList<>();
				short last = row.getLastCellNum();
				for (int i = 0; i < Math.max(last, 0); i++) {
					values.add(cellText(row.getCell(i), formatter));
				}
				if (values.stream().anyMatch((v) -> !v.isBlank())) {
					rows.add(values);
				}
			}
		}
		catch (IOException | RuntimeException ex) {
			if (ex instanceof BadRequestException bad) {
				throw bad;
			}
			throw new BadRequestException("Could not read the Excel file: " + ex.getMessage());
		}
		return rows;
	}

	/** Annexure workbooks often open on a summary tab; the order-level sheet is the one with most rows. */
	private static Sheet pickSheet(Workbook workbook) {
		Sheet best = workbook.getSheetAt(0);
		for (int i = 1; i < workbook.getNumberOfSheets(); i++) {
			Sheet sheet = workbook.getSheetAt(i);
			if (sheet.getPhysicalNumberOfRows() > best.getPhysicalNumberOfRows()) {
				best = sheet;
			}
		}
		return best;
	}

	private static String cellText(Cell cell, DataFormatter formatter) {
		if (cell == null) {
			return "";
		}
		CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
		if (type == CellType.NUMERIC) {
			if (DateUtil.isCellDateFormatted(cell)) {
				LocalDateTime value = cell.getLocalDateTimeCellValue();
				return value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			}
			return java.math.BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
		}
		if (type == CellType.BOOLEAN) {
			return String.valueOf(cell.getBooleanCellValue());
		}
		if (type == CellType.STRING) {
			return cell.getStringCellValue().trim();
		}
		return formatter.formatCellValue(cell).trim();
	}

}
