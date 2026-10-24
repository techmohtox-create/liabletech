package com.liabletech.exportdocs.check;

/** How much a finding matters at the bank's counter. */
public enum Severity {

	/** The bank will call it a discrepancy: payment waits for the buyer's waiver, and a fee is charged. */
	CRITICAL("Discrepancy"),

	/** Likely to be queried, or a data conflict worth fixing before presenting. */
	WARNING("Check"),

	/** Worth knowing; not a discrepancy. */
	INFO("Note");

	private final String label;

	Severity(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

}
