package com.liabletech.exportdocs.reading;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("An exporter's packing list")
public record PackingListReading(
		@JsonPropertyDescription("Packing list number") String documentNumber,
		@JsonPropertyDescription("Packing list date") String documentDate,
		@JsonPropertyDescription("Invoice number the packing list refers to") String invoiceNumber,
		@JsonPropertyDescription("Letter of credit number quoted") String lcNumber,
		@JsonPropertyDescription("Seller / exporter: name and address") String seller,
		@JsonPropertyDescription("Buyer: name and address") String buyer,
		@JsonPropertyDescription("Goods description, verbatim") String goodsDescription,
		@JsonPropertyDescription("Packing lines") List<PackingLine> lines,
		@JsonPropertyDescription("Total number of packages") String totalPackages,
		@JsonPropertyDescription("Kind of package, e.g. BAGS, CARTONS, PALLETS") String packageType,
		@JsonPropertyDescription("Total quantity of goods") String totalQuantity,
		@JsonPropertyDescription("Unit of the total quantity, e.g. MT, KGS, PCS") String quantityUnit,
		@JsonPropertyDescription("Total net weight in kg") String netWeightKg,
		@JsonPropertyDescription("Total gross weight in kg") String grossWeightKg,
		@JsonPropertyDescription("Total measurement in cubic metres") String measurementCbm,
		@JsonPropertyDescription("Shipping marks") String marks,
		@JsonPropertyDescription("Container numbers, comma separated") String containerNumbers,
		@JsonPropertyDescription("YES when signed or stamped, NO when the signature space is empty, \"\" when not visible") String signed,
		@JsonPropertyDescription("Confidence from 0 to 1 that the fields are read correctly") double confidence) {

	public record PackingLine(
			@JsonPropertyDescription("Description of goods on this line") String description,
			@JsonPropertyDescription("Number of packages") String packages,
			@JsonPropertyDescription("Quantity") String quantity,
			@JsonPropertyDescription("Unit, e.g. MT, KGS, PCS") String unit,
			@JsonPropertyDescription("Net weight in kg") String netWeightKg,
			@JsonPropertyDescription("Gross weight in kg") String grossWeightKg) {
	}

}
