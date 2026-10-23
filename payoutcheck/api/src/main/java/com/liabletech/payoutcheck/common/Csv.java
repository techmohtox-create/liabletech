package com.liabletech.payoutcheck.common;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

public final class Csv {

	private Csv() {
	}

	public static String write(List<String> header, List<List<?>> rows) {
		StringWriter out = new StringWriter();
		CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(header.toArray(String[]::new)).get();
		try (CSVPrinter printer = new CSVPrinter(out, format)) {
			for (List<?> row : rows) {
				printer.printRecord(row);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return out.toString();
	}

}
