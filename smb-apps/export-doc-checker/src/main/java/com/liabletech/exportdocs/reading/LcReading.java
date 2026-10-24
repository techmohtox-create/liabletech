package com.liabletech.exportdocs.reading;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The terms of a documentary credit, field by field as in a SWIFT MT700. Read by
 * {@link Mt700Parser} from SWIFT text, or by Claude from a scan.
 */
@JsonClassDescription("A documentary credit (letter of credit), usually a SWIFT MT700 or a bank's printout of one")
public record LcReading(
		@JsonPropertyDescription("Documentary credit number, field 20") String lcNumber,
		@JsonPropertyDescription("Date of issue, field 31C") String issueDate,
		@JsonPropertyDescription("Expiry date, field 31D") String expiryDate,
		@JsonPropertyDescription("Place of expiry, field 31D, e.g. INDIA or the issuing bank's country") String expiryPlace,
		@JsonPropertyDescription("Issuing bank: name and city, or its SWIFT BIC") String issuingBank,
		@JsonPropertyDescription("Applicant (the buyer), field 50: name on the first line, then the address") String applicant,
		@JsonPropertyDescription("Beneficiary (the exporter), field 59: name on the first line, then the address") String beneficiary,
		@JsonPropertyDescription("Currency code, field 32B, e.g. USD") String currency,
		@JsonPropertyDescription("Credit amount, field 32B") String amount,
		@JsonPropertyDescription("Percentage credit amount tolerance, field 39A, as plus/minus, e.g. 05/05") String tolerance,
		@JsonPropertyDescription("Available with ... by ..., field 41a") String availableWith,
		@JsonPropertyDescription("Drafts at, field 42C, e.g. AT SIGHT or 60 DAYS FROM B/L DATE") String draftsAt,
		@JsonPropertyDescription("Partial shipments, field 43P: ALLOWED, NOT ALLOWED or CONDITIONAL") String partialShipments,
		@JsonPropertyDescription("Transhipment, field 43T: ALLOWED, NOT ALLOWED or CONDITIONAL") String transhipment,
		@JsonPropertyDescription("Place of taking in charge or receipt, field 44A") String placeOfReceipt,
		@JsonPropertyDescription("Port of loading or airport of departure, field 44E") String portOfLoading,
		@JsonPropertyDescription("Port of discharge or airport of destination, field 44F") String portOfDischarge,
		@JsonPropertyDescription("Place of final destination or delivery, field 44B") String finalDestination,
		@JsonPropertyDescription("Latest date of shipment, field 44C") String latestShipmentDate,
		@JsonPropertyDescription("Shipment period, field 44D") String shipmentPeriod,
		@JsonPropertyDescription("Description of goods and/or services, field 45A, verbatim with line breaks") String goodsDescription,
		@JsonPropertyDescription("Documents required, field 46A, verbatim, each document on its own line starting with +") String documentsRequired,
		@JsonPropertyDescription("Additional conditions, field 47A, verbatim, each condition on its own line starting with +") String additionalConditions,
		@JsonPropertyDescription("Charges, field 71D") String charges,
		@JsonPropertyDescription("Period for presentation, field 48, as written, e.g. 21/FROM THE DATE OF SHIPMENT") String presentationPeriod,
		@JsonPropertyDescription("Confirmation instructions, field 49: CONFIRM, MAY ADD or WITHOUT") String confirmation,
		@JsonPropertyDescription("Applicable rules, field 40E, e.g. UCP LATEST VERSION") String applicableRules,
		@JsonPropertyDescription("Form of documentary credit, field 40A, e.g. IRREVOCABLE") String form,
		@JsonPropertyDescription("Confidence from 0 to 1 that the fields are read correctly") double confidence) {
}
