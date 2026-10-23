package com.liabletech.materials.reading;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * What Claude reads off a site document. One shape serves delivery challans, weighbridge slips and
 * supplier bills, because supervisors forward all three on the same WhatsApp chat.
 */
@JsonClassDescription("A delivery challan, weighbridge slip or tax invoice for building materials")
public record MaterialDocReading(
		@JsonPropertyDescription("DELIVERY_CHALLAN (delivery challan, DC, gate pass or loading slip), WEIGHBRIDGE_SLIP, "
				+ "TAX_INVOICE (a bill or tax invoice with rates and amounts), or OTHER") DocumentType documentType,
		@JsonPropertyDescription("The supplier's business name as printed") String supplierName,
		@JsonPropertyDescription("The supplier's GSTIN if printed") String supplierGstin,
		@JsonPropertyDescription("Challan number, slip number or invoice number, exactly as printed") String documentNumber,
		@JsonPropertyDescription("Document date, YYYY-MM-DD") String documentDate,
		@JsonPropertyDescription("Truck number, letters and digits only") String vehicleNumber,
		@JsonPropertyDescription("One entry per material line") List<Line> lines,
		@JsonPropertyDescription("On an invoice, every challan or DC number it quotes") List<String> challanReferences,
		@JsonPropertyDescription("Invoice amount before GST, digits only") String taxableAmount,
		@JsonPropertyDescription("Total GST on the invoice, digits only") String taxAmount,
		@JsonPropertyDescription("Invoice grand total, digits only") String totalAmount,
		@JsonPropertyDescription("Confidence from 0 to 1 that the quantities are read correctly") double confidence) {

	public enum DocumentType {

		DELIVERY_CHALLAN, WEIGHBRIDGE_SLIP, TAX_INVOICE, OTHER

	}

	public record Line(
			@JsonPropertyDescription("The material as written, with grade, brand and bar size, e.g. \"UltraTech OPC 53\" or \"TMT Fe500D 12 mm\"") String description,
			@JsonPropertyDescription("Quantity, digits only") String quantity,
			@JsonPropertyDescription("Unit as written: bags, kg, MT, cft, brass, cum, nos") String unit,
			@JsonPropertyDescription("Rate per unit on an invoice, digits only") String rate,
			@JsonPropertyDescription("Line amount on an invoice, digits only") String amount,
			@JsonPropertyDescription("Challan or DC number this invoice line refers to, if shown") String challanReference) {
	}

	static final String INSTRUCTIONS = """
			A photo or scan from an Indian construction site: a supplier's delivery challan, a weighbridge slip, or a \
			supplier's bill / tax invoice for building materials such as cement, TMT steel, sand, aggregate, bricks, \
			blocks or ready-mix concrete. Handwritten quantities are common; read them carefully. For a weighbridge \
			slip report the net weight as one line in kg. For a tax invoice read the rate, amount and challan \
			reference of each line and the taxable, GST and grand totals.""";

}
