package com.liabletech.exportdocs.reading;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("An exporter's commercial invoice")
public record InvoiceReading(
		@JsonPropertyDescription("Invoice number") String invoiceNumber,
		@JsonPropertyDescription("Invoice date") String invoiceDate,
		@JsonPropertyDescription("Seller / exporter: name on the first line, then the address") String seller,
		@JsonPropertyDescription("Buyer the invoice is made out to (bill to): name on the first line, then the address") String buyer,
		@JsonPropertyDescription("Consignee, if shown separately from the buyer") String consignee,
		@JsonPropertyDescription("Letter of credit number quoted on the invoice") String lcNumber,
		@JsonPropertyDescription("Proforma invoice, purchase order or contract number referred to") String orderReference,
		@JsonPropertyDescription("Invoice currency code, e.g. USD") String currency,
		@JsonPropertyDescription("Trade term (Incoterm) such as FOB, CFR, CIF, FCA, CIP") String incoterm,
		@JsonPropertyDescription("Place named with the trade term, e.g. HAMBURG in CIF HAMBURG") String incotermPlace,
		@JsonPropertyDescription("Port of loading") String portOfLoading,
		@JsonPropertyDescription("Port of discharge") String portOfDischarge,
		@JsonPropertyDescription("Final destination") String finalDestination,
		@JsonPropertyDescription("Country of origin of the goods") String countryOfOrigin,
		@JsonPropertyDescription("Vessel or flight") String vesselOrFlight,
		@JsonPropertyDescription("Goods description written above or below the item lines, verbatim") String goodsDescription,
		@JsonPropertyDescription("Item lines") List<InvoiceLine> lines,
		@JsonPropertyDescription("Total quantity") String totalQuantity,
		@JsonPropertyDescription("Unit of the total quantity, e.g. MT, KGS, PCS") String quantityUnit,
		@JsonPropertyDescription("Invoice total amount") String totalAmount,
		@JsonPropertyDescription("Number of packages") String packages,
		@JsonPropertyDescription("Net weight in kg") String netWeightKg,
		@JsonPropertyDescription("Gross weight in kg") String grossWeightKg,
		@JsonPropertyDescription("Shipping marks") String marks,
		@JsonPropertyDescription("YES when signed or stamped by the exporter, NO when the signature space is empty, \"\" when not visible") String signed,
		@JsonPropertyDescription("Confidence from 0 to 1 that the fields are read correctly") double confidence) {

	public record InvoiceLine(
			@JsonPropertyDescription("Description of goods on this line, verbatim") String description,
			@JsonPropertyDescription("HS code") String hsCode,
			@JsonPropertyDescription("Quantity") String quantity,
			@JsonPropertyDescription("Unit, e.g. MT, KGS, PCS") String unit,
			@JsonPropertyDescription("Unit price") String unitPrice,
			@JsonPropertyDescription("Line amount") String amount) {
	}

}
