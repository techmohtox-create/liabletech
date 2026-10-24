package com.liabletech.exportdocs.report;

import java.util.List;

import com.liabletech.exportdocs.check.Finding;
import com.liabletech.exportdocs.check.Fixer;
import com.liabletech.exportdocs.check.Severity;
import com.liabletech.exportdocs.shipment.Shipment;
import com.liabletech.platform.account.Account;

/** The messages that get findings fixed: corrections to the forwarder, an amendment request to the buyer. */
public final class FixMessages {

	private FixMessages() {
	}

	public static List<Finding> forFixer(List<Finding> findings, Fixer fixer) {
		return findings.stream()
			.filter((f) -> f.open() && f.who() == fixer && f.level() != Severity.INFO && f.fix() != null)
			.toList();
	}

	/** Corrections for the forwarder or shipping line, before the original bill of lading is released. */
	public static String forwarder(Account account, Shipment shipment, List<Finding> fixes) {
		StringBuilder text = new StringBuilder();
		text.append("Dear ").append(shipment.forwarderName() == null ? "team" : shipment.forwarderName()).append(",\n\n");
		text.append("Before the original bill of lading is released for our shipment ").append(shipment.reference());
		if (shipment.lcNumber() != null) {
			text.append(" (LC ").append(shipment.lcNumber()).append(")");
		}
		text.append(", please make these corrections so the documents comply with the letter of credit:\n");
		int n = 1;
		for (Finding finding : fixes) {
			text.append('\n').append(n++).append(". ").append(finding.fix());
			if (finding.found() != null) {
				text.append(" (now: ").append(finding.found()).append(")");
			}
		}
		text.append("\n\nPlease send the corrected draft for our approval.\n\nRegards,\n").append(account.name());
		if (account.phone() != null) {
			text.append('\n').append(account.phone());
		}
		return text.toString();
	}

	/** An amendment request to the buyer, field by field. */
	public static String buyer(Account account, Shipment shipment, List<Finding> fixes) {
		StringBuilder text = new StringBuilder();
		text.append("Dear Sir/Madam,\n\n");
		text.append("Re: Letter of credit ").append(shipment.lcNumber() == null ? "" : shipment.lcNumber());
		if (shipment.issuingBank() != null) {
			text.append(" issued by ").append(shipment.issuingBank());
		}
		text.append("\n\nTo let us present documents that comply with the credit, please instruct your bank to amend it as follows:\n");
		int n = 1;
		for (Finding finding : fixes) {
			text.append('\n').append(n++).append(". ").append(finding.fix());
		}
		text.append("\n\nPlease have the amendment advised to us through the advising bank at the earliest.");
		text.append("\n\nThank you,\n").append(account.name());
		if (account.phone() != null) {
			text.append('\n').append(account.phone());
		}
		return text.toString();
	}

	public static String subjectForBuyer(Shipment shipment) {
		return "Request for amendment of LC " + (shipment.lcNumber() == null ? shipment.reference() : shipment.lcNumber());
	}

	public static String subjectForForwarder(Account account, Shipment shipment) {
		return "B/L corrections for " + shipment.reference() + " – " + account.name();
	}

}
