package com.liabletech.freight.invoice;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.openpdf.text.Element;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.liabletech.freight.party.Party;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.pdf.PdfKit;
import com.liabletech.platform.text.IndianMoney;
import com.liabletech.platform.text.Phones;

/**
 * The freight invoice as a PDF, with every proof of delivery appended as a page of its own. One
 * file carries both the bill and the evidence, so "POD not received" is never a reason to wait.
 */
@Component
public class InvoicePdf {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

	private final FileStore files;

	public InvoicePdf(FileStore files) {
		this.files = files;
	}

	public byte[] render(Account account, Party party, Invoice invoice, List<InvoiceLine> lines, List<Proof> proofs,
			String terms) {
		GstMode mode = GstMode.valueOf(invoice.gstMode());
		return PdfKit.build((pdf) -> {
			PdfPTable head = PdfKit.table(60, 40);
			PdfPCell business = PdfKit.plain(new Phrase(), Element.ALIGN_LEFT);
			business.addElement(PdfKit.paragraph(account.name(), PdfKit.bold(15)));
			addIf(business, account.address());
			addIf(business, join("Phone ", Phones.pretty(account.phone()), "   ", account.email()));
			addIf(business, join("GSTIN ", account.gstin(), "   PAN ", account.pan()));
			head.addCell(business);
			PdfPCell title = PdfKit.plain(new Phrase(), Element.ALIGN_RIGHT);
			Paragraph heading = PdfKit.paragraph(mode == GstMode.NONE ? "INVOICE" : "TAX INVOICE", PdfKit.bold(16));
			heading.setAlignment(Element.ALIGN_RIGHT);
			title.addElement(heading);
			for (String line : List.of("No. " + invoice.number(), "Date " + DATE.format(invoice.invoiceDate()),
					"Due " + DATE.format(invoice.dueDate()))) {
				Paragraph p = PdfKit.paragraph(line, PdfKit.font(9.5f));
				p.setAlignment(Element.ALIGN_RIGHT);
				title.addElement(p);
			}
			head.addCell(title);
			pdf.add(head);

			pdf.add(PdfKit.paragraph(" ", PdfKit.font(6)));
			PdfPTable billTo = PdfKit.table(100);
			PdfPCell to = PdfKit.cell("", PdfKit.font(9));
			to.setBackgroundColor(PdfKit.SHADE);
			to.setPadding(8);
			to.addElement(PdfKit.paragraph("Bill to", PdfKit.muted(8.5f)));
			to.addElement(PdfKit.paragraph(party.name(), PdfKit.bold(11)));
			addIf(to, party.address());
			addIf(to, join("GSTIN ", party.gstin(), "", null));
			billTo.addCell(to);
			pdf.add(billTo);

			pdf.add(PdfKit.paragraph(" ", PdfKit.font(6)));
			PdfPTable items = PdfKit.table(6, 70, 24);
			items.setHeaderRows(1);
			items.addCell(PdfKit.headerCell("#", Element.ALIGN_LEFT));
			items.addCell(PdfKit.headerCell("Description", Element.ALIGN_LEFT));
			items.addCell(PdfKit.headerCell("Amount", Element.ALIGN_RIGHT));
			int n = 1;
			for (InvoiceLine line : lines) {
				items.addCell(PdfKit.cell(String.valueOf(n++), PdfKit.font(9)));
				items.addCell(PdfKit.cell(line.description(), PdfKit.font(9)));
				items.addCell(PdfKit.cell(money(line.amount()), PdfKit.font(9), Element.ALIGN_RIGHT));
			}
			pdf.add(items);

			PdfPTable totals = PdfKit.table(64, 22, 14);
			total(totals, "Subtotal", invoice.subtotal(), false);
			if (invoice.cgst().signum() > 0) {
				total(totals, "CGST @ " + half(invoice.gstRate()) + "%", invoice.cgst(), false);
				total(totals, "SGST @ " + half(invoice.gstRate()) + "%", invoice.sgst(), false);
			}
			if (invoice.igst().signum() > 0) {
				total(totals, "IGST @ " + invoice.gstRate().stripTrailingZeros().toPlainString() + "%", invoice.igst(), false);
			}
			if (invoice.advanceReceived().signum() > 0) {
				total(totals, "Less: advance received", invoice.advanceReceived().negate(), false);
			}
			if (invoice.roundOff().signum() != 0) {
				total(totals, "Round off", invoice.roundOff(), false);
			}
			total(totals, "Amount payable", invoice.total(), true);
			pdf.add(totals);
			pdf.add(PdfKit.paragraph(IndianMoney.inWords(invoice.total()), PdfKit.bold(9)));
			if (mode == GstMode.RCM) {
				pdf.add(PdfKit.paragraph("Tax payable on reverse charge basis: Yes. GST is to be paid by the recipient.",
						PdfKit.font(8.5f)));
			}
			else if (mode == GstMode.NONE) {
				pdf.add(PdfKit.paragraph("No GST is charged on this invoice.", PdfKit.muted(8.5f)));
			}

			pdf.add(PdfKit.paragraph(" ", PdfKit.font(6)));
			PdfPTable foot = PdfKit.table(60, 40);
			PdfPCell pay = PdfKit.plain(new Phrase(), Element.ALIGN_LEFT);
			pay.addElement(PdfKit.paragraph("Payment", PdfKit.bold(9.5f)));
			if (account.hasBankDetails()) {
				addIf(pay, join("A/c name ", account.bankAccountName(), "", null));
				addIf(pay, "A/c no. " + account.bankAccountNumber() + "   IFSC " + account.bankIfsc());
				addIf(pay, account.bankName());
			}
			addIf(pay, join("UPI ", account.upiId(), "", null));
			if (StringUtils.hasText(terms)) {
				pay.addElement(PdfKit.paragraph(terms, PdfKit.muted(8)));
			}
			if (!proofs.isEmpty()) {
				pay.addElement(PdfKit.paragraph("Signed proof of delivery attached: " + proofs.size()
						+ (proofs.size() == 1 ? " page." : " pages."), PdfKit.font(8.5f)));
			}
			foot.addCell(pay);
			PdfPCell sign = PdfKit.plain(new Phrase(), Element.ALIGN_RIGHT);
			Paragraph forName = PdfKit.paragraph("For " + account.name(), PdfKit.bold(9.5f));
			forName.setAlignment(Element.ALIGN_RIGHT);
			sign.addElement(forName);
			sign.addElement(PdfKit.paragraph(" ", PdfKit.font(18)));
			Paragraph signatory = PdfKit.paragraph("Authorised signatory", PdfKit.muted(8.5f));
			signatory.setAlignment(Element.ALIGN_RIGHT);
			sign.addElement(signatory);
			foot.addCell(sign);
			pdf.add(foot);

			for (Proof proof : proofs) {
				byte[] bytes = files.read(proof.file());
				if (proof.file().isPdf()) {
					pdf.appendPdf(bytes, proof.caption());
				}
				else {
					pdf.imagePage(bytes, proof.file().contentType(), proof.caption());
				}
			}
		});
	}

	private static void total(PdfPTable table, String label, BigDecimal amount, boolean strong) {
		table.addCell(PdfKit.plain(new Phrase(""), Element.ALIGN_LEFT));
		table.addCell(PdfKit.plain(new Phrase(label, strong ? PdfKit.bold(10) : PdfKit.font(9)), Element.ALIGN_RIGHT));
		table.addCell(PdfKit.plain(new Phrase(money(amount), strong ? PdfKit.bold(10) : PdfKit.font(9)),
				Element.ALIGN_RIGHT));
	}

	private static String money(BigDecimal amount) {
		String rupees = "₹" + IndianMoney.group(amount.abs(), 2);
		return amount.signum() < 0 ? "– " + rupees : rupees;
	}

	private static String half(BigDecimal rate) {
		return rate.divide(BigDecimal.valueOf(2)).stripTrailingZeros().toPlainString();
	}

	private static void addIf(PdfPCell cell, String text) {
		if (StringUtils.hasText(text)) {
			cell.addElement(PdfKit.paragraph(text, PdfKit.font(9)));
		}
	}

	private static String join(String labelA, String a, String labelB, String b) {
		StringBuilder out = new StringBuilder();
		if (StringUtils.hasText(a)) {
			out.append(labelA).append(a);
		}
		if (StringUtils.hasText(b)) {
			out.append(out.isEmpty() ? labelB.trim() + " " : labelB).append(b);
		}
		return out.toString();
	}

	/** A proof-of-delivery page: the photo or scan, with a caption such as "POD · LR 4521". */
	public record Proof(String caption, StoredFile file) {
	}

}
