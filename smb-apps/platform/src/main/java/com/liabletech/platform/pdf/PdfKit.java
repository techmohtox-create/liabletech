package com.liabletech.platform.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfImportedPage;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfWriter;

import com.liabletech.platform.files.Images;

/**
 * Building PDFs (invoices, discrepancy reports) with a font that has the rupee sign. DejaVu Sans
 * is embedded, so "₹" prints correctly on every viewer.
 */
public final class PdfKit {

	public static final Color INK = new Color(31, 41, 51);

	public static final Color MUTED = new Color(107, 114, 128);

	public static final Color LINE = new Color(209, 213, 219);

	public static final Color SHADE = new Color(243, 244, 246);

	private static final BaseFont REGULAR = font("fonts/DejaVuSans.ttf");

	private static final BaseFont BOLD = font("fonts/DejaVuSans-Bold.ttf");

	private PdfKit() {
	}

	/** Builds an A4 document and returns its bytes. */
	public static byte[] build(Consumer<Context> content) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4, 36, 36, 36, 36);
		try {
			PdfWriter writer = PdfWriter.getInstance(document, out);
			document.open();
			content.accept(new Context(document, writer));
			document.close();
		}
		catch (DocumentException ex) {
			throw new IllegalStateException("Could not build PDF", ex);
		}
		return out.toByteArray();
	}

	public static Font font(float size) {
		return new Font(REGULAR, size, Font.NORMAL, INK);
	}

	public static Font bold(float size) {
		return new Font(BOLD, size, Font.NORMAL, INK);
	}

	public static Font muted(float size) {
		return new Font(REGULAR, size, Font.NORMAL, MUTED);
	}

	public static Font colored(float size, Color color, boolean bold) {
		return new Font(bold ? BOLD : REGULAR, size, Font.NORMAL, color);
	}

	public static Paragraph paragraph(String text, Font font) {
		return new Paragraph(text == null ? "" : text, font);
	}

	/** A table cell with a light bottom border and comfortable padding. */
	public static PdfPCell cell(String text, Font font, int align) {
		PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
		cell.setHorizontalAlignment(align);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(5);
		cell.setBorder(Rectangle.BOTTOM);
		cell.setBorderColor(LINE);
		return cell;
	}

	public static PdfPCell cell(String text, Font font) {
		return cell(text, font, Element.ALIGN_LEFT);
	}

	public static PdfPCell headerCell(String text, int align) {
		PdfPCell cell = cell(text, bold(8.5f), align);
		cell.setBackgroundColor(SHADE);
		return cell;
	}

	/** A cell with no border, for label/value layouts. */
	public static PdfPCell plain(Phrase phrase, int align) {
		PdfPCell cell = new PdfPCell(phrase);
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setHorizontalAlignment(align);
		cell.setPadding(2);
		return cell;
	}

	public static PdfPTable table(float... widths) {
		PdfPTable table = new PdfPTable(widths);
		table.setWidthPercentage(100);
		return table;
	}

	public static Phrase labelled(String label, String value) {
		Phrase phrase = new Phrase();
		phrase.add(new Chunk(label + " ", muted(8.5f)));
		phrase.add(new Chunk(value == null ? "–" : value, font(9)));
		return phrase;
	}

	private static BaseFont font(String path) {
		try (InputStream in = PdfKit.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Missing font " + path);
			}
			return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** The document being built, with helpers for pages of evidence after the main content. */
	public record Context(Document document, PdfWriter writer) {

		public void add(Element element) {
			document.add(element);
		}

		/** A new page showing a photo scaled to fit, with a caption above it. */
		public void imagePage(byte[] bytes, String contentType, String caption) {
			Images.Prepared prepared = Images.fit(bytes, contentType, 1800);
			Image image;
			try {
				image = Image.getInstance(prepared.bytes());
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
			document.newPage();
			document.add(paragraph(caption, bold(11)));
			document.add(new Paragraph(" ", font(4)));
			Rectangle page = document.getPageSize();
			float maxWidth = page.getWidth() - document.leftMargin() - document.rightMargin();
			float maxHeight = page.getHeight() - document.topMargin() - document.bottomMargin() - 40;
			image.scaleToFit(maxWidth, maxHeight);
			image.setAlignment(Element.ALIGN_CENTER);
			document.add(image);
		}

		/** Copies every page of another PDF (a forwarded POD scan, say) after the content so far. */
		public void appendPdf(byte[] pdf, String caption) {
			try {
				PdfReader reader = new PdfReader(pdf);
				PdfContentByte canvas = writer.getDirectContent();
				for (int i = 1; i <= reader.getNumberOfPages(); i++) {
					document.newPage();
					if (i == 1) {
						document.add(paragraph(caption, bold(11)));
					}
					PdfImportedPage page = writer.getImportedPage(reader, i);
					Rectangle size = document.getPageSize();
					float scale = Math.min((size.getWidth() - 72) / page.getWidth(),
							(size.getHeight() - 110) / page.getHeight());
					canvas.addTemplate(page, scale, 0, 0, scale, 36, 36);
				}
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

	}

}
