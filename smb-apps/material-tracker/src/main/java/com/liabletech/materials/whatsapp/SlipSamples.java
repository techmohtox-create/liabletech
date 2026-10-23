package com.liabletech.materials.whatsapp;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.liabletech.platform.samples.SampleArt;

/** Stand-in delivery challans and supplier bills for the simulator and the demo site. */
public final class SlipSamples {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

	private SlipSamples() {
	}

	public record Item(String description, String quantity, String unit, String rate) {
	}

	/** A handwritten-style delivery challan with the site's receiving stamp. */
	public static byte[] challan(String supplier, String gstin, String challanNo, LocalDate date, String vehicle, String site,
			List<Item> items) {
		SampleArt art = SampleArt.paper(1300, 1000, challanNo.hashCode());
		art.centered(650, 70, 36, true, supplier.toUpperCase(Locale.ENGLISH))
			.centered(650, 105, 19, false, "Dealers in Cement, TMT Steel, Sand & Aggregates  ·  GSTIN " + gstin)
			.centered(650, 150, 28, true, "DELIVERY CHALLAN")
			.line(60, 170, 1240, 170)
			.text(80, 215, 25, true, "Challan No. " + challanNo)
			.text(800, 215, 24, false, "Date: " + DATE.format(date))
			.text(80, 260, 22, false, "Delivered to: " + site)
			.text(800, 260, 22, false, "Vehicle: " + vehicle)
			.box(60, 290, 1180, 60 + items.size() * 60)
			.text(80, 330, 21, true, "Sr.")
			.text(160, 330, 21, true, "Material")
			.text(800, 330, 21, true, "Qty")
			.text(990, 330, 21, true, "Unit")
			.line(60, 345, 1240, 345);
		int y = 395;
		int n = 1;
		for (Item item : items) {
			art.text(85, y, 22, false, String.valueOf(n++))
				.text(160, y, 22, false, item.description())
				.handwritten(800, y, 28, item.quantity())
				.text(990, y, 22, false, item.unit());
			y += 60;
		}
		art.text(80, y + 80, 20, false, "Goods received in good condition")
			.signature(120, y + 170, 240)
			.text(110, y + 215, 18, false, "Receiver's signature")
			.signature(880, y + 170, 220)
			.text(880, y + 215, 18, false, "For " + supplier);
		return art.jpeg();
	}

	/** A printed tax invoice quoting challan numbers, with rates and amounts. */
	public static byte[] invoice(String supplier, String gstin, String invoiceNo, LocalDate date, String buyer, String site,
			List<String> challans, List<Item> items, String taxable, String gst, String total) {
		SampleArt art = SampleArt.paper(1300, 1150, invoiceNo.hashCode());
		art.centered(650, 70, 36, true, supplier.toUpperCase(Locale.ENGLISH))
			.centered(650, 105, 19, false, "GSTIN " + gstin)
			.centered(650, 150, 30, true, "TAX INVOICE")
			.line(60, 170, 1240, 170)
			.text(80, 215, 24, true, "Invoice No. " + invoiceNo)
			.text(800, 215, 24, false, "Date: " + DATE.format(date))
			.text(80, 260, 22, false, "Buyer: " + buyer)
			.text(80, 300, 22, false, "Site: " + site)
			.text(80, 340, 22, false, "Challan Nos.: " + String.join(", ", challans))
			.box(60, 370, 1180, 70 + items.size() * 60)
			.text(80, 410, 20, true, "Description")
			.text(640, 410, 20, true, "Qty")
			.text(760, 410, 20, true, "Unit")
			.text(880, 410, 20, true, "Rate")
			.text(1060, 410, 20, true, "Amount")
			.line(60, 425, 1240, 425);
		int y = 475;
		for (Item item : items) {
			art.text(80, y, 21, false, item.description())
				.text(640, y, 21, false, item.quantity())
				.text(760, y, 21, false, item.unit())
				.text(880, y, 21, false, item.rate())
				.text(1060, y, 21, false, amount(item));
			y += 60;
		}
		art.text(800, y + 60, 22, false, "Taxable value: " + taxable)
			.text(800, y + 105, 22, false, "GST @ 18%/28%: " + gst)
			.text(800, y + 155, 26, true, "Total: " + total)
			.text(80, y + 230, 18, false, "Payment within 15 days. Interest @ 18% p.a. on delayed payment.")
			.signature(900, y + 300, 220)
			.text(880, y + 345, 18, false, "Authorised signatory");
		return art.jpeg();
	}

	private static String amount(Item item) {
		try {
			return new java.math.BigDecimal(item.quantity()).multiply(new java.math.BigDecimal(item.rate()))
				.setScale(2, java.math.RoundingMode.HALF_UP)
				.toPlainString();
		}
		catch (NumberFormatException ex) {
			return "";
		}
	}

}
