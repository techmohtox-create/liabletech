package com.liabletech.payoutcheck.ingest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.liabletech.payoutcheck.common.BadRequestException;

/**
 * Finds the header row of an export and which column holds each field. Platform annexures start
 * with title and summary rows, so the first 30 rows are scanned and the row matching the most
 * fields wins. Exact header matches are taken first, then headers containing an alias.
 */
public record HeaderMatch(int headerRow, Map<String, Integer> columns, List<String> headers) {

	static final int SCAN_ROWS = 30;

	public static HeaderMatch find(List<List<String>> rows, List<ColumnSpec> specs) {
		HeaderMatch best = null;
		for (int r = 0; r < Math.min(rows.size(), SCAN_ROWS); r++) {
			HeaderMatch candidate = match(r, rows.get(r), specs);
			if (best == null || candidate.columns.size() > best.columns.size()) {
				best = candidate;
			}
		}
		if (best == null) {
			throw new BadRequestException("The file is empty");
		}
		List<String> missing = new ArrayList<>();
		for (ColumnSpec spec : specs) {
			if (spec.required() && !best.columns.containsKey(spec.field())) {
				missing.add(spec.label());
			}
		}
		if (!missing.isEmpty()) {
			throw new BadRequestException("Could not find these columns: " + String.join(", ", missing)
					+ ". Use the template from the Imports page, or rename the headers.");
		}
		return best;
	}

	static HeaderMatch match(int rowIndex, List<String> row, List<ColumnSpec> specs) {
		List<String> keys = row.stream().map(Values::key).toList();
		Map<String, Integer> columns = new LinkedHashMap<>();
		Set<Integer> taken = new HashSet<>();
		for (ColumnSpec spec : specs) {
			for (String alias : spec.aliases()) {
				int index = keys.indexOf(Values.key(alias));
				if (index >= 0 && !taken.contains(index)) {
					columns.put(spec.field(), index);
					taken.add(index);
					break;
				}
			}
		}
		for (ColumnSpec spec : specs) {
			if (columns.containsKey(spec.field())) {
				continue;
			}
			search: for (String alias : spec.aliases()) {
				String aliasKey = Values.key(alias);
				if (aliasKey.length() < 5) {
					continue;
				}
				for (int i = 0; i < keys.size(); i++) {
					String key = keys.get(i);
					if (!taken.contains(i) && key.contains(aliasKey) && spec.excludes().stream().noneMatch(key::contains)) {
						columns.put(spec.field(), i);
						taken.add(i);
						break search;
					}
				}
			}
		}
		return new HeaderMatch(rowIndex, columns, row);
	}

	public String value(List<String> row, String field) {
		Integer index = this.columns.get(field);
		if (index == null || index >= row.size()) {
			return null;
		}
		return row.get(index);
	}

	public boolean has(String field) {
		return this.columns.containsKey(field);
	}

	/** Which file header each field was read from, for the import report. */
	public Map<String, String> describe() {
		Map<String, String> mapping = new LinkedHashMap<>();
		this.columns.forEach((field, index) -> mapping.put(field, this.headers.get(index)));
		return mapping;
	}

}
