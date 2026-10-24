package com.liabletech.exportdocs.document;

import java.util.List;

import com.liabletech.exportdocs.reading.InsuranceReading;
import com.liabletech.exportdocs.reading.InvoiceReading;
import com.liabletech.exportdocs.reading.LcReading;
import com.liabletech.exportdocs.reading.OriginReading;
import com.liabletech.exportdocs.reading.PackingListReading;
import com.liabletech.exportdocs.reading.TransportDocReading;

/** The documents of an LC shipment that are read and checked against each other. */
public enum DocType {

	LC("Letter of credit", "LC", LcReading.class, """
			A letter of credit (documentary credit) received by an Indian exporter, usually a SWIFT MT700 printed by \
			the advising bank. Field numbers in the descriptions refer to MT700 tags. If an amendment (MT707) is \
			included, report the credit as amended. Copy fields 45A, 46A and 47A verbatim, keeping each document or \
			condition on its own line."""),

	INVOICE("Commercial invoice", "Invoice", InvoiceReading.class, """
			An Indian exporter's commercial invoice for goods shipped under a letter of credit. Read every item line. \
			Amounts are in the invoice currency (often USD or EUR), not rupees, unless the invoice says INR."""),

	PACKING_LIST("Packing list", "Packing list", PackingListReading.class, """
			An Indian exporter's packing list: packages, quantities and weights. Report totals as printed; when no \
			total is printed, leave it empty rather than adding up the lines."""),

	TRANSPORT("Bill of lading / air waybill", "B/L", TransportDocReading.class, """
			A transport document for an export shipment: an ocean bill of lading, sea waybill, multimodal transport \
			document or air waybill. Look for the "shipped on board" or "laden on board" notation and its date, which \
			is often a stamp. Report any clause about the condition of the goods or packaging verbatim."""),

	ORIGIN("Certificate of origin", "Origin", OriginReading.class, """
			A certificate of origin for exported goods, such as one issued by a chamber of commerce, the Export \
			Inspection Council or a preferential certificate."""),

	INSURANCE("Insurance document", "Insurance", InsuranceReading.class, """
			A marine cargo insurance policy or certificate for an export shipment."""),

	OTHER("Other document", "Other", null, "");

	private final String label;

	private final String shortLabel;

	private final Class<? extends Record> readingType;

	private final String instructions;

	DocType(String label, String shortLabel, Class<? extends Record> readingType, String instructions) {
		this.label = label;
		this.shortLabel = shortLabel;
		this.readingType = readingType;
		this.instructions = instructions;
	}

	public String label() {
		return label;
	}

	public String shortLabel() {
		return shortLabel;
	}

	public Class<? extends Record> readingType() {
		return readingType;
	}

	public String instructions() {
		return instructions;
	}

	/** The documents with a reading, in the order they are shown. */
	public static List<DocType> read() {
		return List.of(LC, INVOICE, PACKING_LIST, TRANSPORT, ORIGIN, INSURANCE);
	}

	public static DocType parse(String value) {
		if (value == null) {
			return null;
		}
		for (DocType type : values()) {
			if (type.name().equalsIgnoreCase(value.trim())) {
				return type;
			}
		}
		return null;
	}

}
