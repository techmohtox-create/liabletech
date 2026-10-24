package com.liabletech.exportdocs.check;

/** Who has to act to clear a finding. */
public enum Fixer {

	EXPORTER("Your documents"),

	FORWARDER("Forwarder / shipping line"),

	BUYER("Buyer: LC amendment"),

	INSURER("Insurer"),

	CHAMBER("Certificate issuer");

	private final String label;

	Fixer(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

}
