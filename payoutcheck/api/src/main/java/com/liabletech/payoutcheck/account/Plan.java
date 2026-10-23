package com.liabletech.payoutcheck.account;

public enum Plan {

	STARTER("Starter", 199, 1, 1, false), GROWTH("Growth", 349, 3, 3, true), PRO("Pro", 499, 10, 10, true);

	private final String label;

	private final int monthlyPriceInr;

	private final int maxOutlets;

	private final int maxUsers;

	private final boolean disputeLetters;

	Plan(String label, int monthlyPriceInr, int maxOutlets, int maxUsers, boolean disputeLetters) {
		this.label = label;
		this.monthlyPriceInr = monthlyPriceInr;
		this.maxOutlets = maxOutlets;
		this.maxUsers = maxUsers;
		this.disputeLetters = disputeLetters;
	}

	public String label() {
		return this.label;
	}

	public int monthlyPriceInr() {
		return this.monthlyPriceInr;
	}

	public int maxOutlets() {
		return this.maxOutlets;
	}

	public int maxUsers() {
		return this.maxUsers;
	}

	public boolean disputeLetters() {
		return this.disputeLetters;
	}

}
