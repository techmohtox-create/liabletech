package com.liabletech.parts.catalog;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liabletech.platform.text.Lenient;

/**
 * The stock list as a spreadsheet, in and out. Columns are found by their heading, in any order and
 * as most billing software exports them ("Part No", "Item Name", "Qty", "Rate"). A part number
 * already in the list updates that part.
 */
@Service
public class StockCsv {

	public static final List<String> HEADINGS = List.of("part_number", "name", "category", "brand", "price", "mrp", "stock",
			"rack", "fits", "alt_numbers");

	private static final Map<String, List<String>> ALIASES = new LinkedHashMap<>();

	static {
		ALIASES.put("part_number", List.of("part_number", "part no", "part number", "partno", "part #", "number", "sku",
				"item code", "code"));
		ALIASES.put("name", List.of("name", "item name", "description", "item", "part name", "product"));
		ALIASES.put("category", List.of("category", "type", "part type", "group"));
		ALIASES.put("brand", List.of("brand", "make", "company", "manufacturer"));
		ALIASES.put("price", List.of("price", "selling price", "rate", "sale price", "net price", "dealer price"));
		ALIASES.put("mrp", List.of("mrp", "m.r.p.", "list price"));
		ALIASES.put("stock", List.of("stock", "qty", "quantity", "on hand", "closing stock", "balance"));
		ALIASES.put("rack", List.of("rack", "bin", "location", "shelf"));
		ALIASES.put("fits", List.of("fits", "vehicles", "vehicle", "fitment", "fitments", "application", "model", "models"));
		ALIASES.put("alt_numbers", List.of("alt_numbers", "alt numbers", "alternate numbers", "oem", "oem number",
				"oem numbers", "cross reference", "replaces"));
	}

	private final Parts parts;

	private final Clock clock;

	public StockCsv(Parts parts, Clock clock) {
		this.parts = parts;
		this.clock = clock;
	}

	@Transactional
	public Result importCsv(long accountId, byte[] bytes) {
		List<List<String>> rows = parse(new String(bytes, StandardCharsets.UTF_8).replace("﻿", ""));
		if (rows.isEmpty()) {
			return new Result(0, 0, List.of("The file is empty."));
		}
		Map<String, Integer> columns = columns(rows.getFirst());
		if (!columns.containsKey("part_number") || !columns.containsKey("name") || !columns.containsKey("price")) {
			return new Result(0, 0, List.of("The first row needs headings, including part_number, name and price."));
		}
		OffsetDateTime now = OffsetDateTime.now(clock);
		int added = 0;
		int updated = 0;
		List<String> problems = new ArrayList<>();
		for (int i = 1; i < rows.size(); i++) {
			List<String> row = rows.get(i);
			if (row.stream().allMatch(String::isBlank)) {
				continue;
			}
			int line = i + 1;
			String number = value(row, columns, "part_number");
			String name = value(row, columns, "name");
			BigDecimal price = Lenient.number(value(row, columns, "price"));
			if (number == null || name == null) {
				problems.add("Line " + line + ": no part number or name.");
				continue;
			}
			if (price == null || price.signum() <= 0) {
				problems.add("Line " + line + " (" + number + "): no price.");
				continue;
			}
			Optional<Part> existing = parts.byNumber(accountId, number);
			String category = value(row, columns, "category");
			PartType type = Optional.ofNullable(PartType.parse(category))
				.filter((t) -> t != PartType.OTHER)
				.or(() -> existing.map(Part::type).filter((t) -> !columns.containsKey("category")))
				.or(() -> PartType.find(name))
				.orElse(PartType.OTHER);
			BigDecimal stock = Lenient.number(value(row, columns, "stock"));
			// A column the file does not have keeps what the part already has.
			Parts.PartData data = new Parts.PartData(number, name, type,
					keep(columns, row, "brand", existing.map(Part::brand)),
					keep(columns, row, "alt_numbers", existing.map(Part::altNumbers)), price,
					columns.containsKey("mrp") ? Lenient.number(value(row, columns, "mrp")) : existing.map(Part::mrp).orElse(null),
					columns.containsKey("stock") ? (stock == null ? 0 : Math.max(0, stock.intValue()))
							: existing.map(Part::stock).orElse(0),
					keep(columns, row, "rack", existing.map(Part::rack)));
			long id;
			if (existing.isPresent()) {
				id = existing.get().id();
				parts.update(accountId, id, data, now);
				updated++;
			}
			else {
				id = parts.create(accountId, data, now);
				added++;
			}
			if (columns.containsKey("fits")) {
				parts.replaceFitments(id, fitments(value(row, columns, "fits")));
			}
		}
		return new Result(added, updated, problems);
	}

	/** "Swift 2018-2024; Dzire 2017-2024 | Baleno" as fitments. */
	public static List<Fitment> fitments(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		return Arrays.stream(text.split("[;|/\\n]+")).map(Fitment::parse).filter(Objects::nonNull).toList();
	}

	public byte[] export(long accountId) {
		List<Part> all = parts.list(accountId, null, null, null, 100_000);
		Map<Long, List<Fitment>> fits = parts.fitments(all.stream().map(Part::id).toList());
		StringBuilder csv = new StringBuilder(String.join(",", HEADINGS)).append("\r\n");
		for (Part part : all) {
			String fitText = String.join("; ", fits.getOrDefault(part.id(), List.of()).stream().map(Fitment::describe).toList());
			csv.append(String.join(",", List.of(cell(part.partNumber()), cell(part.name()), part.category(),
					cell(part.brand()), part.price().toPlainString(), part.mrp() == null ? "" : part.mrp().toPlainString(),
					String.valueOf(part.stock()), cell(part.rack()), cell(fitText), cell(part.altNumbers()))))
				.append("\r\n");
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static Map<String, Integer> columns(List<String> headings) {
		Map<String, Integer> columns = new LinkedHashMap<>();
		for (int i = 0; i < headings.size(); i++) {
			String heading = headings.get(i).trim().toLowerCase(Locale.ROOT).replace('_', ' ').replaceAll("\\s+", " ");
			for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
				boolean known = entry.getValue().stream().anyMatch((alias) -> alias.replace('_', ' ').equals(heading));
				if (known && !columns.containsKey(entry.getKey())) {
					columns.put(entry.getKey(), i);
				}
			}
		}
		return columns;
	}

	private static String keep(Map<String, Integer> columns, List<String> row, String name, Optional<String> current) {
		return columns.containsKey(name) ? value(row, columns, name) : current.orElse(null);
	}

	private static String value(List<String> row, Map<String, Integer> columns, String name) {
		Integer index = columns.get(name);
		if (index == null || index >= row.size()) {
			return null;
		}
		String value = row.get(index).trim();
		return value.isEmpty() ? null : value;
	}

	private static String cell(String value) {
		if (value == null) {
			return "";
		}
		return value.contains(",") || value.contains("\"") || value.contains("\n") ? "\"" + value.replace("\"", "\"\"") + "\""
				: value;
	}

	/** Comma-separated values with double quotes around values that hold commas, quotes or line breaks. */
	public static List<List<String>> parse(String text) {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quoted) {
				if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
					cell.append('"');
					i++;
				}
				else if (c == '"') {
					quoted = false;
				}
				else {
					cell.append(c);
				}
			}
			else if (c == '"') {
				quoted = true;
			}
			else if (c == ',') {
				row.add(cell.toString());
				cell.setLength(0);
			}
			else if (c == '\n' || c == '\r') {
				if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
					i++;
				}
				row.add(cell.toString());
				cell.setLength(0);
				rows.add(row);
				row = new ArrayList<>();
			}
			else {
				cell.append(c);
			}
		}
		if (!cell.isEmpty() || !row.isEmpty()) {
			row.add(cell.toString());
			rows.add(row);
		}
		return rows;
	}

	/** What an import did, with the lines it could not use. */
	public record Result(int added, int updated, List<String> problems) {
	}

}
