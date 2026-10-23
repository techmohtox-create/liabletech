package com.liabletech.payoutcheck.ingest;

import java.util.List;

import com.liabletech.payoutcheck.common.Csv;

/** Blank import templates, using the first alias of each field as its header. */
public final class Templates {

	private Templates() {
	}

	public static String orders() {
		return Csv.write(headers(Formats.ORDERS),
				List.of(List.of("SWG-100234", "2026-10-13 13:05", "Delivered", "Swiggy", "450.00", "20.00", "40.00")));
	}

	public static String statement() {
		return Csv.write(headers(Formats.STATEMENT),
				List.of(List.of("SWG-100234", "2026-10-13", "Delivered", "450.00", "20.00", "40.00", "77.40", "8.60", "0", "15.48",
						"0", "0.43", "0", "0", "0", "0", "328.09", ""),
						List.of("", "", "", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "-500.00", "-500.00",
								"Ads deduction: Oct campaign")));
	}

	private static List<String> headers(List<ColumnSpec> specs) {
		return specs.stream().map((spec) -> spec.aliases().get(0)).toList();
	}

}
