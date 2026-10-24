package com.liabletech.exportdocs.report;

import java.awt.Color;
import java.time.LocalDate;
import java.util.List;

import org.openpdf.text.Element;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;

import com.liabletech.exportdocs.check.Finding;
import com.liabletech.exportdocs.check.LcTerms;
import com.liabletech.exportdocs.check.RequiredDoc;
import com.liabletech.exportdocs.check.Severity;
import com.liabletech.exportdocs.check.TradeText;
import com.liabletech.exportdocs.document.DocType;
import com.liabletech.exportdocs.shipment.Shipment;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.pdf.PdfKit;

/**
 * The pre-presentation check as a PDF: what the credit asks for, what the documents say, and every
 * discrepancy with the rule behind it, to keep with the file or send to the bank's trade desk.
 */
public final class ReportPdf {

	private static final Color BAD = new Color(185, 28, 28);

	private static final Color WARN = new Color(180, 83, 9);

	private static final Color OK = new Color(21, 128, 61);

	private ReportPdf() {
	}

	public static byte[] render(Account account, Shipment shipment, LcTerms terms, List<Finding> findings,
			List<DocType> uploaded, LocalDate today) {
		return PdfKit.build((pdf) -> {
			pdf.add(PdfKit.paragraph("Document check before presentation", PdfKit.bold(15)));
			pdf.add(PdfKit.paragraph(account.name() + " · " + shipment.reference()
					+ (shipment.title() == null ? "" : " · " + shipment.title()) + " · checked " + TradeText.show(today),
					PdfKit.muted(9)));
			pdf.add(new Paragraph(" ", PdfKit.font(6)));

			PdfPTable facts = PdfKit.table(1, 1);
			facts.addCell(PdfKit.plain(PdfKit.labelled("LC number", shipment.lcNumber()), Element.ALIGN_LEFT));
			facts.addCell(PdfKit.plain(PdfKit.labelled("Issuing bank", shipment.issuingBank()), Element.ALIGN_LEFT));
			facts.addCell(PdfKit.plain(PdfKit.labelled("Applicant", shipment.buyerName()), Element.ALIGN_LEFT));
			facts.addCell(PdfKit.plain(PdfKit.labelled("Amount", TradeText.money(shipment.currency(), shipment.lcAmount())),
					Element.ALIGN_LEFT));
			facts.addCell(PdfKit.plain(PdfKit.labelled("Latest shipment", TradeText.show(shipment.latestShipment())),
					Element.ALIGN_LEFT));
			facts.addCell(PdfKit.plain(PdfKit.labelled("Expiry", TradeText.show(shipment.lcExpiry())
					+ (terms != null && terms.expiryPlace() != null ? " in " + terms.expiryPlace() : "")), Element.ALIGN_LEFT));
			facts.addCell(PdfKit.plain(PdfKit.labelled("Shipped on", TradeText.show(shipment.shippedOn())), Element.ALIGN_LEFT));
			facts.addCell(PdfKit.plain(PdfKit.labelled("Present by", TradeText.show(shipment.presentationDeadline())),
					Element.ALIGN_LEFT));
			pdf.add(facts);
			pdf.add(new Paragraph(" ", PdfKit.font(6)));

			List<Finding> open = findings.stream().filter(Finding::open).toList();
			long critical = open.stream().filter((f) -> f.level() == Severity.CRITICAL).count();
			long warnings = open.stream().filter((f) -> f.level() == Severity.WARNING).count();
			String verdict = critical > 0 ? critical + (critical == 1 ? " discrepancy" : " discrepancies") + " to fix before presenting"
					: warnings > 0 ? warnings + (warnings == 1 ? " point" : " points") + " to check before presenting"
							: "No discrepancies found";
			pdf.add(PdfKit.paragraph(verdict, PdfKit.colored(12, critical > 0 ? BAD : warnings > 0 ? WARN : OK, true)));
			if (terms != null && terms.conditions().discrepancyFee() != null && critical > 0) {
				pdf.add(PdfKit.paragraph("The credit charges a discrepancy fee of " + terms.conditions().discrepancyFee()
						+ " per discrepant presentation (47A), and payment waits for the buyer's waiver.", PdfKit.muted(8.5f)));
			}
			pdf.add(new Paragraph(" ", PdfKit.font(6)));

			if (!open.isEmpty()) {
				PdfPTable table = PdfKit.table(1.4f, 4.0f, 2.2f, 1.4f);
				table.setHeaderRows(1);
				table.addCell(PdfKit.headerCell("Level", Element.ALIGN_LEFT));
				table.addCell(PdfKit.headerCell("Finding", Element.ALIGN_LEFT));
				table.addCell(PdfKit.headerCell("Credit / documents", Element.ALIGN_LEFT));
				table.addCell(PdfKit.headerCell("Who fixes", Element.ALIGN_LEFT));
				for (Finding finding : open) {
					Color color = switch (finding.level()) {
						case CRITICAL -> BAD;
						case WARNING -> WARN;
						default -> PdfKit.MUTED;
					};
					table.addCell(PdfKit.cell(finding.level().label(), PdfKit.colored(8.5f, color, true)));
					Phrase what = new Phrase();
					what.add(new Phrase(finding.title() + "\n", PdfKit.bold(8.5f)));
					what.add(new Phrase(finding.detail(), PdfKit.font(8)));
					if (finding.ruleRef() != null) {
						what.add(new Phrase("\n" + finding.ruleRef(), PdfKit.muted(7.5f)));
					}
					PdfPCell whatCell = PdfKit.cell(null, PdfKit.font(8));
					whatCell.setPhrase(what);
					table.addCell(whatCell);
					String values = (finding.expected() == null ? "" : "Credit: " + finding.expected())
							+ (finding.found() == null ? "" : (finding.expected() == null ? "" : "\n") + "Found: " + finding.found());
					table.addCell(PdfKit.cell(values, PdfKit.font(7.5f)));
					table.addCell(PdfKit.cell(finding.who().label(), PdfKit.font(8)));
				}
				pdf.add(table);
				pdf.add(new Paragraph(" ", PdfKit.font(6)));
			}

			if (terms != null && !terms.requiredDocs().isEmpty()) {
				pdf.add(PdfKit.paragraph("Documents the credit asks for (46A)", PdfKit.bold(11)));
				PdfPTable table = PdfKit.table(0.4f, 2.4f, 1.6f, 1.2f);
				table.setHeaderRows(1);
				table.addCell(PdfKit.headerCell("#", Element.ALIGN_LEFT));
				table.addCell(PdfKit.headerCell("Document", Element.ALIGN_LEFT));
				table.addCell(PdfKit.headerCell("Originals / copies", Element.ALIGN_LEFT));
				table.addCell(PdfKit.headerCell("Status", Element.ALIGN_LEFT));
				for (RequiredDoc doc : terms.requiredDocs()) {
					table.addCell(PdfKit.cell(String.valueOf(doc.number()), PdfKit.font(8)));
					table.addCell(PdfKit.cell(doc.kind().label(), PdfKit.font(8.5f)));
					table.addCell(PdfKit.cell(doc.countText(), PdfKit.font(8)));
					String status = doc.readAs() == null ? "Prepare (not checked here)"
							: uploaded.contains(doc.readAs()) ? "Checked" : "Not uploaded";
					table.addCell(PdfKit.cell(status, PdfKit.font(8)));
				}
				pdf.add(table);
			}
			pdf.add(new Paragraph(" ", PdfKit.font(8)));
			pdf.add(PdfKit.paragraph("Checked against the credit's terms, UCP 600 and international standard banking practice "
					+ "by reading the documents provided. The bank's examination is final; keep this report with the file.",
					PdfKit.muted(7.5f)));
		});
	}

}
