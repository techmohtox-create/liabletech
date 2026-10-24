package com.liabletech.exportdocs.check;

import java.util.List;
import java.util.stream.Collectors;

import com.liabletech.exportdocs.document.DocType;

/**
 * A finding before it is stored.
 *
 * @param key distinguishes findings of the same code (which document, which field), so a decision
 * on one survives a re-check
 * @param fix the instruction for whoever fixes it, used in the messages to the forwarder and buyer
 */
public record FindingDraft(Severity severity, String code, String key, String title, String detail, String expected,
		String found, List<DocType> documents, String ruleRef, Fixer fixer, String fix) {

	public String fingerprint() {
		return key == null || key.isBlank() ? code : code + ":" + key;
	}

	public String documentList() {
		return documents.stream().map(DocType::name).collect(Collectors.joining(","));
	}

}
