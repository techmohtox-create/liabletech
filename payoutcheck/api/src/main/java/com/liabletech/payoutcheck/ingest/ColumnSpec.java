package com.liabletech.payoutcheck.ingest;

import java.util.List;

/**
 * One field we want from a file and the headers it goes by. {@code excludes} keeps a loose match
 * from grabbing a neighbouring column: "Taxes on commission" must not be read as commission.
 */
public record ColumnSpec(String field, String label, boolean required, List<String> aliases, List<String> excludes) {

	public static ColumnSpec required(String field, String label, String... aliases) {
		return new ColumnSpec(field, label, true, List.of(aliases), List.of());
	}

	public static ColumnSpec optional(String field, String label, String... aliases) {
		return new ColumnSpec(field, label, false, List.of(aliases), List.of());
	}

	public ColumnSpec excluding(String... words) {
		return new ColumnSpec(this.field, this.label, this.required, this.aliases, List.of(words));
	}

}
