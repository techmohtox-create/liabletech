package com.liabletech.exportdocs.whatsapp;

import java.time.LocalDate;
import java.util.List;

import com.liabletech.exportdocs.reading.InsuranceReading;
import com.liabletech.exportdocs.reading.InvoiceReading;
import com.liabletech.exportdocs.reading.LcReading;
import com.liabletech.exportdocs.reading.OriginReading;
import com.liabletech.exportdocs.reading.PackingListReading;
import com.liabletech.exportdocs.reading.TransportDocReading;

/**
 * Fictional letters of credit with their documents, dated around a given day. The pepper set has
 * the mistakes exporters often make; the towel set complies; the brass credit has terms worth an
 * amendment before shipping.
 */
public final class SampleSet {

	public static final String EXPORTER = "KAVERI OVERSEAS";

	public static final String EXPORTER_ADDRESS = "KAVERI OVERSEAS\n41/2104 MG ROAD, ERNAKULAM\nKOCHI 682016, KERALA, INDIA";

	private SampleSet() {
	}

	public record Docs(LcReading lc, InvoiceReading invoice, PackingListReading packing, TransportDocReading transport,
			OriginReading origin, InsuranceReading insurance) {
	}

	/** Black pepper, CIF Hamburg: shipped two days late, with five discrepancies across the set. */
	public static Docs pepper(LocalDate today, String lcNumber) {
		String applicant = "NORDSEE SPICE IMPORT GMBH\nGROSSE ELBSTRASSE 145\n22767 HAMBURG, GERMANY";
		LcReading lc = new LcReading(lcNumber, d(today.minusDays(42)), d(today.plusDays(14)), "INDIA",
				"HAFENSTADT BANK AG, HAMBURG", applicant, EXPORTER_ADDRESS, "USD", "85000", "05/05",
				"ANY BANK IN INDIA\nBY NEGOTIATION", "AT SIGHT", "NOT ALLOWED", "ALLOWED", "", "COCHIN, INDIA",
				"HAMBURG, GERMANY", "", d(today.minusDays(6)), "",
				"INDIAN BLACK PEPPER ASTA GRADE 550 G/L, MACHINE CLEANED\nQUANTITY: 20 MT\nUNIT PRICE: USD 4,250.00 PER MT\n"
						+ "CIF HAMBURG, GERMANY INCOTERMS 2020\nAS PER PROFORMA INVOICE NO. KO/PI/26/118",
				"+SIGNED COMMERCIAL INVOICE IN 3 ORIGINALS SHOWING THIS L/C NUMBER\n"
						+ "+PACKING LIST IN 3 COPIES SHOWING GROSS AND NET WEIGHT\n"
						+ "+FULL SET 3/3 OF CLEAN ON BOARD OCEAN BILLS OF LADING MADE OUT TO THE ORDER OF HAFENSTADT BANK AG, "
						+ "MARKED FREIGHT PREPAID AND NOTIFY APPLICANT\n"
						+ "+INSURANCE CERTIFICATE IN DUPLICATE, BLANK ENDORSED, FOR 110 PCT OF CIF VALUE, COVERING INSTITUTE "
						+ "CARGO CLAUSES (A), INSTITUTE WAR CLAUSES AND INSTITUTE STRIKES CLAUSES\n"
						+ "+CERTIFICATE OF ORIGIN ISSUED BY CHAMBER OF COMMERCE\n"
						+ "+BENEFICIARY'S CERTIFICATE THAT ONE SET OF NON-NEGOTIABLE DOCUMENTS HAS BEEN SENT TO THE APPLICANT "
						+ "WITHIN 5 DAYS OF SHIPMENT",
				"+ALL DOCUMENTS MUST SHOW THIS L/C NUMBER.\n"
						+ "+A DISCREPANCY FEE OF USD 80.00 WILL BE DEDUCTED FROM THE PROCEEDS OF EACH DISCREPANT PRESENTATION.\n"
						+ "+DOCUMENTS DATED PRIOR TO THE L/C ISSUANCE DATE ARE NOT ACCEPTABLE.",
				"ALL BANKING CHARGES OUTSIDE GERMANY ARE FOR BENEFICIARY'S ACCOUNT", "21/FROM THE DATE OF SHIPMENT",
				"WITHOUT", "UCP LATEST VERSION", "IRREVOCABLE", 1);
		String invoiceNo = "KO/EXP/26-27/0142";
		String marks = "NSI/HAM/26\nPEPPER 550 GL\nBAG 1-800";
		InvoiceReading invoice = new InvoiceReading(invoiceNo, d(today.minusDays(5)), EXPORTER_ADDRESS,
				"NORDSEE SPICE IMPORT GMBH\nGROSSE ELBSTRASSE 145, 22767 HAMBURG, GERMANY", "TO ORDER", lcNumber,
				"KO/PI/26/118", "USD", "CIF", "HAMBURG", "COCHIN, INDIA", "HAMBURG, GERMANY", "", "INDIA",
				"MV OCEAN PEARL V.26E", "",
				List.of(new InvoiceReading.InvoiceLine("BLACK PEPPER 550 G/L", "09041120", "20", "MT", "4250.00", "85000.00")),
				"20", "MT", "85000.00", "800", "20000", "20450", marks, "YES", 0.95);
		PackingListReading packing = new PackingListReading("KO/PL/26-27/0142", d(today.minusDays(5)), invoiceNo,
				lcNumber, EXPORTER_ADDRESS, "NORDSEE SPICE IMPORT GMBH\nHAMBURG, GERMANY",
				"INDIAN BLACK PEPPER ASTA GRADE 550 G/L, MACHINE CLEANED",
				List.of(new PackingListReading.PackingLine("BLACK PEPPER 550 G/L IN 25 KG PP BAGS", "800", "20000", "KGS",
						"20000", "20450")),
				"800", "PP BAGS", "20000", "KGS", "20000", "20450", "33.5", marks, "MCLU4417286", "YES", 0.95);
		TransportDocReading transport = new TransportDocReading("BILL_OF_LADING", "MCCL/COK/HAM/26/0381",
				d(today.minusDays(4)), d(today.minusDays(4)), "YES", "MALABAR COAST CONTAINER LINE",
				"AS AGENT FOR THE CARRIER MALABAR COAST CONTAINER LINE", EXPORTER_ADDRESS,
				"TO THE ORDER OF HAFENSTADT BANK AG", "NORDSEE SPICE IMPORT GMBH\nGROSSE ELBSTRASSE 145, 22767 HAMBURG",
				"MV OCEAN PEARL", "26E", "COCHIN", "COCHIN, INDIA", "HAMBURG, GERMANY", "HAMBURG",
				"800 PP BAGS BLACK PEPPER", "800 BAGS", "20540", "33.5", marks, "MCLU4417286", "COLLECT", "3", "",
				lcNumber, invoiceNo, 0.93);
		OriginReading origin = new OriginReading("ICC/KOC/26/58812", d(today.minusDays(3)),
				"INDIAN CHAMBER OF COMMERCE, KOCHI", EXPORTER_ADDRESS, "NORDSEE SPICE IMPORT GMBH, HAMBURG", "INDIA",
				"BY SEA FROM COCHIN TO HAMBURG PER MV OCEAN PEARL", "BLACK PEPPER", "800 BAGS / 20 MT", "20450", invoiceNo,
				lcNumber, "YES", 0.94);
		InsuranceReading insurance = new InsuranceReading("CERTIFICATE", "MCI/26/77514", d(today.minusDays(5)), "",
				EXPORTER, "USD", "93500.00", "INSTITUTE CARGO CLAUSES (A) AND INSTITUTE WAR CLAUSES (CARGO)",
				"COCHIN, INDIA", "HAMBURG, GERMANY", "MV OCEAN PEARL", "HAMBURG IN USD", invoiceNo, lcNumber,
				"BLANK ENDORSED", 0.94);
		return new Docs(lc, invoice, packing, transport, origin, insurance);
	}

	/** Cotton bath towels, FOB Tuticorin: everything agrees. */
	public static Docs towels(LocalDate today, String lcNumber) {
		String applicant = "AL NOOR TEXTILES TRADING LLC\nPO BOX 18834, DEIRA\nDUBAI, UNITED ARAB EMIRATES";
		LcReading lc = new LcReading(lcNumber, d(today.minusDays(30)), d(today.plusDays(31)), "INDIA",
				"GULF CRESCENT BANK PJSC, DUBAI", applicant, EXPORTER_ADDRESS, "USD", "42600", "10/10",
				"ANY BANK IN INDIA\nBY NEGOTIATION", "AT SIGHT", "ALLOWED", "ALLOWED", "", "TUTICORIN, INDIA",
				"JEBEL ALI, UAE", "", d(today.plusDays(10)), "",
				"100% COTTON TERRY BATH TOWELS 500 GSM, ASSORTED COLOURS\nQUANTITY: 12,000 PCS\n"
						+ "UNIT PRICE: USD 3.55 PER PC\nFOB TUTICORIN, INDIA INCOTERMS 2020",
				"+SIGNED COMMERCIAL INVOICE IN 3 ORIGINALS\n+PACKING LIST IN 2 COPIES\n"
						+ "+FULL SET 3/3 OF CLEAN ON BOARD OCEAN BILLS OF LADING MADE OUT TO THE ORDER OF GULF CRESCENT BANK PJSC, "
						+ "MARKED FREIGHT COLLECT AND NOTIFY APPLICANT",
				"+THIRD PARTY DOCUMENTS EXCEPT INVOICE ARE ACCEPTABLE.", "ALL CHARGES OUTSIDE UAE ARE FOR BENEFICIARY",
				"21/FROM THE DATE OF SHIPMENT", "WITHOUT", "UCP LATEST VERSION", "IRREVOCABLE", 1);
		String invoiceNo = "KO/EXP/26-27/0151";
		String marks = "AL NOOR/DXB\nC/NO 1-300";
		InvoiceReading invoice = new InvoiceReading(invoiceNo, d(today.minusDays(3)), EXPORTER_ADDRESS,
				"AL NOOR TEXTILES TRADING LLC\nPO BOX 18834, DEIRA, DUBAI, UAE", "TO ORDER", lcNumber, "", "USD", "FOB",
				"TUTICORIN", "TUTICORIN, INDIA", "JEBEL ALI, UAE", "", "INDIA", "MV GULF SPIRIT V.112W", "",
				List.of(new InvoiceReading.InvoiceLine("100% COTTON TERRY BATH TOWELS 500 GSM, ASSORTED COLOURS", "63026010",
						"12000", "PCS", "3.55", "42600.00")),
				"12000", "PCS", "42600.00", "300", "6600", "7050", marks, "YES", 0.96);
		PackingListReading packing = new PackingListReading("KO/PL/26-27/0151", d(today.minusDays(3)), invoiceNo,
				lcNumber, EXPORTER_ADDRESS, "AL NOOR TEXTILES TRADING LLC, DUBAI", "COTTON TERRY BATH TOWELS",
				List.of(new PackingListReading.PackingLine("COTTON BATH TOWELS, 40 PCS PER CARTON", "300", "12000", "PCS",
						"6600", "7050")),
				"300", "CARTONS", "12000", "PCS", "6600", "7050", "28.6", marks, "GCSU7720415", "YES", 0.96);
		TransportDocReading transport = new TransportDocReading("BILL_OF_LADING", "GSL/TUT/JEA/26/1187",
				d(today.minusDays(2)), d(today.minusDays(2)), "YES", "GULF STAR LINES",
				"AS AGENT FOR THE CARRIER GULF STAR LINES", EXPORTER_ADDRESS, "TO THE ORDER OF GULF CRESCENT BANK PJSC",
				"AL NOOR TEXTILES TRADING LLC, PO BOX 18834, DUBAI", "MV GULF SPIRIT", "112W", "TUTICORIN",
				"TUTICORIN, INDIA", "JEBEL ALI, UAE", "JEBEL ALI", "300 CARTONS COTTON BATH TOWELS", "300 CARTONS", "7050",
				"28.6", marks, "GCSU7720415", "COLLECT", "3", "", lcNumber, invoiceNo, 0.95);
		return new Docs(lc, invoice, packing, transport, null, null);
	}

	/**
	 * Brass handicrafts, CIF New York, before shipment: the credit expires abroad, allows only 15
	 * days to present, and needs an inspection certificate signed for the buyer.
	 */
	public static LcReading brass(LocalDate today, String lcNumber) {
		return new LcReading(lcNumber, d(today.minusDays(6)), d(today.plusDays(38)), "USA",
				"EMPIRE MERCHANTS BANK, NEW YORK", "HUDSON HOME DECOR INC\n225 W 37TH ST, NEW YORK, NY 10018, USA",
				EXPORTER_ADDRESS, "USD", "18750", "", "EMPIRE MERCHANTS BANK, NEW YORK\nBY PAYMENT", "AT SIGHT",
				"NOT ALLOWED", "ALLOWED", "", "ANY SEAPORT IN INDIA", "NEW YORK, USA", "", d(today.plusDays(21)), "",
				"HANDCRAFTED BRASS TABLE LAMPS AND CANDLE HOLDERS\nQUANTITY: 1,500 PCS\nCIF NEW YORK INCOTERMS 2020\n"
						+ "AS PER PURCHASE ORDER NO. HHD-PO-4471",
				"+SIGNED COMMERCIAL INVOICE IN 3 ORIGINALS\n+PACKING LIST IN 2 COPIES\n"
						+ "+FULL SET OF CLEAN ON BOARD OCEAN BILLS OF LADING CONSIGNED TO HUDSON HOME DECOR INC, MARKED FREIGHT "
						+ "PREPAID AND NOTIFY APPLICANT\n"
						+ "+INSURANCE POLICY FOR 110 PCT OF CIF VALUE COVERING ALL RISKS\n"
						+ "+INSPECTION CERTIFICATE SIGNED BY APPLICANT'S REPRESENTATIVE",
				"+ALL DOCUMENTS MUST SHOW THIS CREDIT NUMBER.\n+A DISCREPANCY FEE OF USD 95.00 APPLIES.",
				"ALL CHARGES OUTSIDE USA ARE FOR BENEFICIARY", "15/FROM THE DATE OF SHIPMENT", "WITHOUT", "UCP LATEST VERSION",
				"IRREVOCABLE", 1);
	}

	private static String d(LocalDate date) {
		return date.toString();
	}

}
