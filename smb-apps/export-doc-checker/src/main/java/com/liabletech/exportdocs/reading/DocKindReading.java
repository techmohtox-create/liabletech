package com.liabletech.exportdocs.reading;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Which export document a photo or scan is, when its text alone does not say. */
@JsonClassDescription("The kind of export or trade document")
public record DocKindReading(
		@JsonPropertyDescription("LC (letter of credit or its amendment), INVOICE (commercial invoice), PACKING_LIST, "
				+ "TRANSPORT (bill of lading, sea waybill or air waybill), ORIGIN (certificate of origin), "
				+ "INSURANCE (insurance policy or certificate), or OTHER") String documentType,
		@JsonPropertyDescription("Confidence from 0 to 1") double confidence) {
}
