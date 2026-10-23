package com.liabletech.freight.pod;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** What Claude reads off a driver's photo of the lorry receipt. Field descriptions go into the schema. */
@JsonClassDescription("The details on a photographed Indian lorry receipt or proof-of-delivery copy")
public record PodReading(
		@JsonPropertyDescription("POD: a lorry receipt or delivery copy signed and/or stamped by the receiver. "
				+ "LR_COPY: a lorry receipt with no delivery acknowledgement. INVOICE: a tax invoice or bill. "
				+ "OTHER: anything else") DocumentType documentType,
		@JsonPropertyDescription("The consignment note number, called LR, GR, CN, bilty or builty number. "
				+ "Not the invoice number or the e-way bill number") String lrNumber,
		@JsonPropertyDescription("The LR (booking) date, YYYY-MM-DD") String lrDate,
		@JsonPropertyDescription("The date the receiver took delivery, as written or stamped near the stamp or "
				+ "signature, YYYY-MM-DD") String deliveryDate,
		@JsonPropertyDescription("Consignor (sender) name") String consignor,
		@JsonPropertyDescription("Consignee (receiver) name") String consignee,
		@JsonPropertyDescription("City the goods were loaded in") String fromCity,
		@JsonPropertyDescription("City the goods were delivered to") String toCity,
		@JsonPropertyDescription("Truck registration number, letters and digits only, e.g. MH12AB1234") String vehicleNumber,
		@JsonPropertyDescription("Number of packages or articles with their unit, e.g. \"120 bags\"") String packages,
		@JsonPropertyDescription("Freight amount in rupees if written on the receipt, digits only") String freightAmount,
		@JsonPropertyDescription("True if the receiver's rubber stamp or seal is visible") boolean stampPresent,
		@JsonPropertyDescription("True if the receiver's signature is visible") boolean signaturePresent,
		@JsonPropertyDescription("Remarks the receiver wrote about shortage, damage or condition, in English") String deliveryRemarks,
		@JsonPropertyDescription("Confidence from 0 to 1 that the LR number and dates are read correctly") double confidence) {

	public enum DocumentType {

		POD, LR_COPY, INVOICE, OTHER

	}

	static final String INSTRUCTIONS = """
			A photo, taken by a truck driver after unloading, of an Indian lorry receipt (LR, also called \
			consignment note, GR or bilty) or its proof-of-delivery (POD) copy. The receiver usually signs it, \
			stamps it with the company seal and writes the delivery date by hand. The LR number is printed or \
			handwritten near the top, often next to "LR No.", "C/N No.", "G.R. No." or "Bilty No.". Read the \
			receiver's handwritten remarks such as "received 118 bags, 2 short" or "received in good condition".""";

}
