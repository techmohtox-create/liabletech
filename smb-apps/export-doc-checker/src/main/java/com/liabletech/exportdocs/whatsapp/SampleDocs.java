package com.liabletech.exportdocs.whatsapp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.openpdf.text.Element;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;

import com.liabletech.exportdocs.check.TradeText;
import com.liabletech.exportdocs.reading.InsuranceReading;
import com.liabletech.exportdocs.reading.InvoiceReading;
import com.liabletech.exportdocs.reading.LcReading;
import com.liabletech.exportdocs.reading.OriginReading;
import com.liabletech.exportdocs.reading.PackingListReading;
import com.liabletech.exportdocs.reading.TransportDocReading;
import com.liabletech.platform.pdf.PdfKit;

/**
 * Sample export documents as PDFs with a text layer, for the demo and the WhatsApp simulator. The
 * letter of credit is laid out like an advising bank's MT700 printout, so it is read by the same
 * parser as a real one.
 */
public final class SampleDocs {

	private static final DateTimeFormatter SWIFT = DateTimeFormatter.ofPattern("yyMMdd");

	private static final DateTimeFormatter PRINTED = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private SampleDocs() {
	}

	/** An MT700 as an advising bank prints it: tag, label, then the value on the following lines. */
	public static byte[] lc(LcReading lc, String sender, String receiver) {
		return PdfKit.build((pdf) -> {
			pdf.add(PdfKit.paragraph("INCOMING SWIFT MESSAGE – MT700 ISSUE OF A DOCUMENTARY CREDIT", PdfKit.bold(10)));
			pdf.add(PdfKit.paragraph("Sender: " + sender, PdfKit.font(8.5f)));
			pdf.add(PdfKit.paragraph("Receiver: " + receiver, PdfKit.font(8.5f)));
			pdf.add(PdfKit.paragraph("------------------------------------------------------------------------", PdfKit.muted(8)));
			field(pdf, "27", "Sequence of Total", "1/1");
			field(pdf, "40A", "Form of Documentary Credit", lc.form());
			field(pdf, "20", "Documentary Credit Number", lc.lcNumber());
			field(pdf, "31C", "Date of Issue", swift(lc.issueDate()));
			field(pdf, "40E", "Applicable Rules", lc.applicableRules());
			field(pdf, "31D", "Date and Place of Expiry", swift(lc.expiryDate()) + lc.expiryPlace());
			field(pdf, "50", "Applicant", lc.applicant());
			field(pdf, "59", "Beneficiary", "/0047112233445\n" + lc.beneficiary());
			field(pdf, "32B", "Currency Code, Amount", lc.currency() + new BigDecimal(lc.amount()).setScale(2).toPlainString().replace('.', ','));
			field(pdf, "39A", "Percentage Credit Amount Tolerance", lc.tolerance());
			field(pdf, "41D", "Available With ... By ...", lc.availableWith());
			field(pdf, "42C", "Drafts at ...", lc.draftsAt());
			field(pdf, "43P", "Partial Shipments", lc.partialShipments());
			field(pdf, "43T", "Transhipment", lc.transhipment());
			field(pdf, "44E", "Port of Loading/Airport of Departure", lc.portOfLoading());
			field(pdf, "44F", "Port of Discharge/Airport of Destination", lc.portOfDischarge());
			field(pdf, "44C", "Latest Date of Shipment", swift(lc.latestShipmentDate()));
			field(pdf, "45A", "Description of Goods and/or Services", lc.goodsDescription());
			field(pdf, "46A", "Documents Required", lc.documentsRequired());
			field(pdf, "47A", "Additional Conditions", lc.additionalConditions());
			field(pdf, "71D", "Charges", lc.charges());
			field(pdf, "48", "Period for Presentation in Days", lc.presentationPeriod());
			field(pdf, "49", "Confirmation Instructions", lc.confirmation());
			pdf.add(PdfKit.paragraph("------------------------------------------------------------------------", PdfKit.muted(8)));
			pdf.add(PdfKit.paragraph("End of Message", PdfKit.muted(8)));
		});
	}

	public static byte[] invoice(InvoiceReading inv) {
		return PdfKit.build((pdf) -> {
			pdf.add(centered("COMMERCIAL INVOICE", PdfKit.bold(14)));
			pdf.add(new Paragraph(" ", PdfKit.font(4)));
			PdfPTable head = PdfKit.table(1, 1);
			head.addCell(box("Exporter", inv.seller()));
			head.addCell(box("Invoice no. and date", inv.invoiceNumber() + "\n" + printed(inv.invoiceDate())
					+ (inv.orderReference().isBlank() ? "" : "\nBuyer's order: " + inv.orderReference())
					+ (inv.lcNumber().isBlank() ? "" : "\nL/C No.: " + inv.lcNumber())));
			head.addCell(box("Buyer", inv.buyer()));
			head.addCell(box("Consignee", inv.consignee().isBlank() ? "TO ORDER" : inv.consignee()));
			head.addCell(box("Port of loading / discharge", inv.portOfLoading() + " / " + inv.portOfDischarge()));
			head.addCell(box("Vessel · terms · origin", inv.vesselOrFlight() + "\n" + inv.incoterm() + " " + inv.incotermPlace()
					+ "\nCountry of origin: " + inv.countryOfOrigin()));
			pdf.add(head);
			if (!inv.goodsDescription().isBlank()) {
				pdf.add(new Paragraph(" ", PdfKit.font(4)));
				pdf.add(PdfKit.paragraph(inv.goodsDescription(), PdfKit.font(9)));
			}
			pdf.add(new Paragraph(" ", PdfKit.font(4)));
			PdfPTable lines = PdfKit.table(4.2f, 1.2f, 1.3f, 0.8f, 1.4f, 1.6f);
			for (String header : List.of("Description of goods", "HS code", "Quantity", "Unit", "Rate " + inv.currency(),
					"Amount " + inv.currency())) {
				lines.addCell(PdfKit.headerCell(header, Element.ALIGN_LEFT));
			}
			for (InvoiceReading.InvoiceLine line : inv.lines()) {
				lines.addCell(PdfKit.cell(line.description(), PdfKit.font(8.5f)));
				lines.addCell(PdfKit.cell(line.hsCode(), PdfKit.font(8.5f)));
				lines.addCell(PdfKit.cell(line.quantity(), PdfKit.font(8.5f), Element.ALIGN_RIGHT));
				lines.addCell(PdfKit.cell(line.unit(), PdfKit.font(8.5f)));
				lines.addCell(PdfKit.cell(line.unitPrice(), PdfKit.font(8.5f), Element.ALIGN_RIGHT));
				lines.addCell(PdfKit.cell(line.amount(), PdfKit.font(8.5f), Element.ALIGN_RIGHT));
			}
			PdfPCell total = PdfKit.cell("Total " + inv.incoterm() + " " + inv.incotermPlace(), PdfKit.bold(9));
			total.setColspan(5);
			lines.addCell(total);
			lines.addCell(PdfKit.cell(inv.currency() + " " + inv.totalAmount(), PdfKit.bold(9), Element.ALIGN_RIGHT));
			pdf.add(lines);
			pdf.add(new Paragraph(" ", PdfKit.font(4)));
			pdf.add(PdfKit.paragraph("Packages: " + inv.packages() + "   Net weight: " + inv.netWeightKg() + " kg   Gross weight: "
					+ inv.grossWeightKg() + " kg", PdfKit.font(9)));
			pdf.add(PdfKit.paragraph("Marks: " + inv.marks(), PdfKit.font(9)));
			pdf.add(new Paragraph(" ", PdfKit.font(10)));
			pdf.add(PdfKit.paragraph("We certify that this invoice shows the actual price of the goods described and that all particulars are true and correct.",
					PdfKit.muted(8)));
			signature(pdf, inv.seller(), "YES".equalsIgnoreCase(inv.signed()));
		});
	}

	public static byte[] packingList(PackingListReading pl) {
		return PdfKit.build((pdf) -> {
			pdf.add(centered("PACKING LIST", PdfKit.bold(14)));
			pdf.add(new Paragraph(" ", PdfKit.font(4)));
			PdfPTable head = PdfKit.table(1, 1);
			head.addCell(box("Exporter", pl.seller()));
			head.addCell(box("Packing list no. and date", pl.documentNumber() + "\n" + printed(pl.documentDate())
					+ "\nInvoice no.: " + pl.invoiceNumber() + (pl.lcNumber().isBlank() ? "" : "\nL/C No.: " + pl.lcNumber())));
			head.addCell(box("Buyer", pl.buyer()));
			head.addCell(box("Marks and containers", pl.marks() + "\n" + pl.containerNumbers()));
			pdf.add(head);
			pdf.add(new Paragraph(" ", PdfKit.font(4)));
			PdfPTable lines = PdfKit.table(4, 1.3f, 1.3f, 0.8f, 1.4f, 1.4f);
			for (String header : List.of("Description", "Packages", "Quantity", "Unit", "Net kg", "Gross kg")) {
				lines.addCell(PdfKit.headerCell(header, Element.ALIGN_LEFT));
			}
			for (PackingListReading.PackingLine line : pl.lines()) {
				lines.addCell(PdfKit.cell(line.description(), PdfKit.font(8.5f)));
				lines.addCell(PdfKit.cell(line.packages(), PdfKit.font(8.5f), Element.ALIGN_RIGHT));
				lines.addCell(PdfKit.cell(line.quantity(), PdfKit.font(8.5f), Element.ALIGN_RIGHT));
				lines.addCell(PdfKit.cell(line.unit(), PdfKit.font(8.5f)));
				lines.addCell(PdfKit.cell(line.netWeightKg(), PdfKit.font(8.5f), Element.ALIGN_RIGHT));
				lines.addCell(PdfKit.cell(line.grossWeightKg(), PdfKit.font(8.5f), Element.ALIGN_RIGHT));
			}
			lines.addCell(PdfKit.cell("Total", PdfKit.bold(9)));
			lines.addCell(PdfKit.cell(pl.totalPackages() + " " + pl.packageType(), PdfKit.bold(9), Element.ALIGN_RIGHT));
			lines.addCell(PdfKit.cell(pl.totalQuantity(), PdfKit.bold(9), Element.ALIGN_RIGHT));
			lines.addCell(PdfKit.cell(pl.quantityUnit(), PdfKit.bold(9)));
			lines.addCell(PdfKit.cell(pl.netWeightKg(), PdfKit.bold(9), Element.ALIGN_RIGHT));
			lines.addCell(PdfKit.cell(pl.grossWeightKg(), PdfKit.bold(9), Element.ALIGN_RIGHT));
			pdf.add(lines);
			pdf.add(PdfKit.paragraph("Measurement: " + pl.measurementCbm() + " CBM", PdfKit.font(9)));
			signature(pdf, pl.seller(), true);
		});
	}

	public static byte[] billOfLading(TransportDocReading bl) {
		return PdfKit.build((pdf) -> {
			pdf.add(centered(bl.carrier().toUpperCase(Locale.ROOT), PdfKit.bold(12)));
			pdf.add(centered("BILL OF LADING FOR OCEAN TRANSPORT OR MULTIMODAL TRANSPORT", PdfKit.bold(10)));
			pdf.add(centered("B/L No. " + bl.documentNumber(), PdfKit.font(9)));
			pdf.add(new Paragraph(" ", PdfKit.font(4)));
			PdfPTable head = PdfKit.table(1, 1);
			head.addCell(box("Shipper", bl.shipper()));
			head.addCell(box("Consignee", bl.consignee()));
			head.addCell(box("Notify party", bl.notifyParty()));
			head.addCell(box("Vessel / voyage", bl.vessel() + " / " + bl.voyage()));
			head.addCell(box("Port of loading", bl.portOfLoading()));
			head.addCell(box("Port of discharge", bl.portOfDischarge()));
			head.addCell(box("Place of receipt", bl.placeOfReceipt()));
			head.addCell(box("Place of delivery", bl.placeOfDelivery()));
			pdf.add(head);
			pdf.add(new Paragraph(" ", PdfKit.font(4)));
			PdfPTable goods = PdfKit.table(2.2f, 1.2f, 3.6f, 1.3f, 1.2f);
			for (String header : List.of("Marks / containers", "Packages", "Description of goods", "Gross weight", "Measurement")) {
				goods.addCell(PdfKit.headerCell(header, Element.ALIGN_LEFT));
			}
			goods.addCell(PdfKit.cell(bl.marks() + "\n" + bl.containerNumbers(), PdfKit.font(8.5f)));
			goods.addCell(PdfKit.cell(bl.packages(), PdfKit.font(8.5f)));
			goods.addCell(PdfKit.cell(bl.goodsDescription()
					+ (bl.lcNumber().isBlank() ? "" : "\nL/C No. " + bl.lcNumber())
					+ (bl.invoiceNumber().isBlank() ? "" : "\nInvoice No. " + bl.invoiceNumber())
					+ (bl.clauses().isBlank() ? "" : "\n" + bl.clauses()), PdfKit.font(8.5f)));
			goods.addCell(PdfKit.cell(bl.grossWeightKg() + " KGS", PdfKit.font(8.5f)));
			goods.addCell(PdfKit.cell(bl.measurementCbm() + " CBM", PdfKit.font(8.5f)));
			pdf.add(goods);
			pdf.add(new Paragraph(" ", PdfKit.font(4)));
			pdf.add(PdfKit.paragraph("FREIGHT " + bl.freight(), PdfKit.bold(10)));
			pdf.add(PdfKit.paragraph("Number of original B/Ls: " + bl.originalsIssued(), PdfKit.font(9)));
			pdf.add(PdfKit.paragraph("Place and date of issue: COCHIN, " + printed(bl.issueDate()), PdfKit.font(9)));
			if ("YES".equalsIgnoreCase(bl.shippedOnBoard())) {
				pdf.add(new Paragraph(" ", PdfKit.font(6)));
				pdf.add(PdfKit.paragraph("SHIPPED ON BOARD " + printed(bl.onBoardDate()) + " · VESSEL " + bl.vessel(),
						PdfKit.colored(11, new java.awt.Color(29, 78, 216), true)));
			}
			pdf.add(new Paragraph(" ", PdfKit.font(10)));
			pdf.add(PdfKit.paragraph("Signed " + bl.signedBy(), PdfKit.font(9)));
		});
	}

	public static byte[] origin(OriginReading o) {
		return PdfKit.build((pdf) -> {
			pdf.add(centered("CERTIFICATE OF ORIGIN", PdfKit.bold(14)));
			pdf.add(centered(o.issuer(), PdfKit.font(9)));
			pdf.add(new Paragraph(" ", PdfKit.font(4)));
			PdfPTable head = PdfKit.table(1, 1);
			head.addCell(box("Exporter", o.exporter()));
			head.addCell(box("Certificate no. and date", o.certificateNumber() + "\n" + printed(o.issueDate())));
			head.addCell(box("Consignee", o.consignee()));
			head.addCell(box("Transport", o.transportDetails()));
			head.addCell(box("Description of goods", o.goodsDescription() + "\nInvoice no. " + o.invoiceNumber()));
			head.addCell(box("Quantity and weight", o.quantity() + "\nGross " + o.grossWeightKg() + " kg"));
			pdf.add(head);
			pdf.add(new Paragraph(" ", PdfKit.font(6)));
			pdf.add(PdfKit.paragraph("It is hereby certified that the goods described above originate in " + o.countryOfOrigin() + ".",
					PdfKit.font(9)));
			signature(pdf, o.issuer(), true);
		});
	}

	public static byte[] insurance(InsuranceReading s) {
		return PdfKit.build((pdf) -> {
			pdf.add(centered("MARINE CARGO INSURANCE " + s.documentType(), PdfKit.bold(13)));
			pdf.add(centered("No. " + s.documentNumber() + " · issued " + printed(s.issueDate()), PdfKit.font(9)));
			pdf.add(new Paragraph(" ", PdfKit.font(4)));
			PdfPTable head = PdfKit.table(1, 1);
			head.addCell(box("Insured", s.insured()));
			head.addCell(box("Sum insured", s.currency() + " " + s.insuredAmount()));
			head.addCell(box("Voyage", "From " + s.voyageFrom() + " to " + s.voyageTo() + "\nPer " + s.conveyance()));
			head.addCell(box("Claims payable at", s.claimsPayableAt()));
			head.addCell(box("Conditions", s.risks()));
			head.addCell(box("References", "Invoice " + s.invoiceNumber() + "\nL/C " + s.lcNumber()));
			pdf.add(head);
			pdf.add(PdfKit.paragraph(s.endorsement(), PdfKit.font(9)));
			signature(pdf, "the insurer", true);
		});
	}

	private static void field(PdfKit.Context pdf, String tag, String label, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		pdf.add(PdfKit.paragraph(":" + tag + ": " + label, PdfKit.bold(8.5f)));
		for (String line : value.split("\\R")) {
			Paragraph paragraph = PdfKit.paragraph(line, PdfKit.font(8.5f));
			paragraph.setIndentationLeft(24);
			pdf.add(paragraph);
		}
	}

	private static PdfPCell box(String label, String value) {
		Phrase phrase = new Phrase();
		phrase.add(new Phrase(label + "\n", PdfKit.muted(7.5f)));
		phrase.add(new Phrase(value == null ? "" : value, PdfKit.font(9)));
		PdfPCell cell = new PdfPCell(phrase);
		cell.setPadding(5);
		cell.setBorderColor(PdfKit.LINE);
		return cell;
	}

	private static Paragraph centered(String text, org.openpdf.text.Font font) {
		Paragraph paragraph = PdfKit.paragraph(text, font);
		paragraph.setAlignment(Element.ALIGN_CENTER);
		return paragraph;
	}

	private static void signature(PdfKit.Context pdf, String party, boolean signed) {
		pdf.add(new Paragraph(" ", PdfKit.font(12)));
		String name = TradeText.firstLine(party);
		Paragraph sign = PdfKit.paragraph((signed ? "Sd/-  " : "") + "For " + (name == null ? "" : name) + "\nAuthorised signatory",
				PdfKit.font(9));
		sign.setAlignment(Element.ALIGN_RIGHT);
		pdf.add(sign);
	}

	private static String swift(String isoDate) {
		LocalDate date = TradeText.date(isoDate);
		return date == null ? "" : SWIFT.format(date);
	}

	private static String printed(String isoDate) {
		LocalDate date = TradeText.date(isoDate);
		return date == null ? (isoDate == null ? "" : isoDate) : PRINTED.format(date);
	}

}
