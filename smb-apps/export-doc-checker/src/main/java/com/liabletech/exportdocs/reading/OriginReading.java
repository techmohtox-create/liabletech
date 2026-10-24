package com.liabletech.exportdocs.reading;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("A certificate of origin")
public record OriginReading(
		@JsonPropertyDescription("Certificate number") String certificateNumber,
		@JsonPropertyDescription("Date of issue") String issueDate,
		@JsonPropertyDescription("Body that issued or certified it, e.g. a chamber of commerce or Export Inspection Council") String issuer,
		@JsonPropertyDescription("Exporter: name and address") String exporter,
		@JsonPropertyDescription("Consignee: name and address") String consignee,
		@JsonPropertyDescription("Country of origin of the goods") String countryOfOrigin,
		@JsonPropertyDescription("Means of transport and route") String transportDetails,
		@JsonPropertyDescription("Description of goods, verbatim") String goodsDescription,
		@JsonPropertyDescription("Quantity with its unit") String quantity,
		@JsonPropertyDescription("Gross weight in kg") String grossWeightKg,
		@JsonPropertyDescription("Invoice number referred to") String invoiceNumber,
		@JsonPropertyDescription("Letter of credit number quoted") String lcNumber,
		@JsonPropertyDescription("YES when signed and stamped by the issuing body, NO when not, \"\" when not visible") String signed,
		@JsonPropertyDescription("Confidence from 0 to 1 that the fields are read correctly") double confidence) {
}
