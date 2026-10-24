package com.liabletech.exportdocs.reading;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * The text layer of a PDF. Most export documents are generated from accounting or ERP software and
 * carry one; SWIFT letters of credit always do. Scans have none.
 */
public final class PdfText {

	private static final int MAX_PAGES = 20;

	private PdfText() {
	}

	/** The PDF's text in reading order, or "" for a scan, an encrypted file or something that is not a PDF. */
	public static String extract(byte[] pdf) {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			stripper.setEndPage(Math.min(document.getNumberOfPages(), MAX_PAGES));
			return stripper.getText(document);
		}
		catch (IOException | RuntimeException ex) {
			return "";
		}
	}

	/** Whether there is enough text to work with rather than a scanned image. */
	public static boolean hasText(String text) {
		return text != null && text.replaceAll("\\s+", "").length() >= 80;
	}

}
