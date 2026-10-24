package com.liabletech.exportdocs.reading;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("A transport document: ocean bill of lading, sea waybill, multimodal bill of lading or air waybill")
public record TransportDocReading(
		@JsonPropertyDescription("BILL_OF_LADING, SEA_WAYBILL, MULTIMODAL, CHARTER_PARTY_BL, AIR_WAYBILL or OTHER") String documentType,
		@JsonPropertyDescription("Bill of lading or air waybill number") String documentNumber,
		@JsonPropertyDescription("Date of issue") String issueDate,
		@JsonPropertyDescription("Date in the shipped / laden on board notation, or the flight date on an air waybill") String onBoardDate,
		@JsonPropertyDescription("YES when it says the goods were shipped or laden on board a named vessel, NO when it only says received for shipment") String shippedOnBoard,
		@JsonPropertyDescription("Carrier's name") String carrier,
		@JsonPropertyDescription("How it is signed, as written, e.g. AS AGENT FOR THE CARRIER MAERSK") String signedBy,
		@JsonPropertyDescription("Shipper") String shipper,
		@JsonPropertyDescription("Consignee, as written, e.g. TO THE ORDER OF ABC BANK") String consignee,
		@JsonPropertyDescription("Notify party") String notifyParty,
		@JsonPropertyDescription("Vessel name") String vessel,
		@JsonPropertyDescription("Voyage number") String voyage,
		@JsonPropertyDescription("Place of receipt") String placeOfReceipt,
		@JsonPropertyDescription("Port of loading or airport of departure") String portOfLoading,
		@JsonPropertyDescription("Port of discharge or airport of destination") String portOfDischarge,
		@JsonPropertyDescription("Place of delivery") String placeOfDelivery,
		@JsonPropertyDescription("Description of goods, verbatim") String goodsDescription,
		@JsonPropertyDescription("Number of packages") String packages,
		@JsonPropertyDescription("Gross weight in kg") String grossWeightKg,
		@JsonPropertyDescription("Measurement in cubic metres") String measurementCbm,
		@JsonPropertyDescription("Shipping marks") String marks,
		@JsonPropertyDescription("Container numbers, comma separated") String containerNumbers,
		@JsonPropertyDescription("Freight marking: PREPAID, COLLECT, or as written") String freight,
		@JsonPropertyDescription("Number of originals issued, e.g. 3") String originalsIssued,
		@JsonPropertyDescription("Any clause or notation about damaged goods or packaging, verbatim; \"\" when there is none") String clauses,
		@JsonPropertyDescription("Letter of credit number quoted") String lcNumber,
		@JsonPropertyDescription("Invoice number quoted") String invoiceNumber,
		@JsonPropertyDescription("Confidence from 0 to 1 that the fields are read correctly") double confidence) {
}
