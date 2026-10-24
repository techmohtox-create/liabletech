package com.liabletech.exportdocs.reading;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("A marine cargo insurance policy, certificate or declaration")
public record InsuranceReading(
		@JsonPropertyDescription("POLICY, CERTIFICATE, DECLARATION or COVER_NOTE") String documentType,
		@JsonPropertyDescription("Policy or certificate number") String documentNumber,
		@JsonPropertyDescription("Date of issue") String issueDate,
		@JsonPropertyDescription("Date cover is effective from, if stated separately") String effectiveDate,
		@JsonPropertyDescription("Insured party, as written") String insured,
		@JsonPropertyDescription("Currency of the insured amount") String currency,
		@JsonPropertyDescription("Insured amount") String insuredAmount,
		@JsonPropertyDescription("Risks covered, verbatim, e.g. INSTITUTE CARGO CLAUSES (A), WAR AND STRIKES") String risks,
		@JsonPropertyDescription("Voyage from (port or place)") String voyageFrom,
		@JsonPropertyDescription("Voyage to (port or place)") String voyageTo,
		@JsonPropertyDescription("Vessel or conveyance") String conveyance,
		@JsonPropertyDescription("Where claims are payable") String claimsPayableAt,
		@JsonPropertyDescription("Invoice number referred to") String invoiceNumber,
		@JsonPropertyDescription("Letter of credit number quoted") String lcNumber,
		@JsonPropertyDescription("Endorsement, e.g. BLANK ENDORSED") String endorsement,
		@JsonPropertyDescription("Confidence from 0 to 1 that the fields are read correctly") double confidence) {
}
