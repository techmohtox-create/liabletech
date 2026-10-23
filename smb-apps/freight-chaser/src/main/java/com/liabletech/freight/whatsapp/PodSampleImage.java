package com.liabletech.freight.whatsapp;

import java.awt.Color;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.liabletech.platform.samples.SampleArt;

/** Draws a signed and stamped lorry receipt, as a driver would photograph it after unloading. */
public final class PodSampleImage {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

	private PodSampleImage() {
	}

	public static byte[] render(String transporter, String lrNumber, LocalDate lrDate, LocalDate deliveredOn,
			String vehicle, String from, String to, String consignor, String consignee, String goods, int packages,
			String remark) {
		SampleArt art = SampleArt.paper(1400, 1000, lrNumber.hashCode());
		art.centered(700, 70, 38, true, transporter.toUpperCase(Locale.ENGLISH))
			.centered(700, 108, 20, false, "Fleet Owners & Transport Contractors")
			.centered(700, 150, 26, true, "LORRY RECEIPT / CONSIGNMENT NOTE  –  CONSIGNEE COPY")
			.line(60, 170, 1340, 170)
			.text(80, 220, 26, true, "LR No.  " + lrNumber)
			.text(820, 220, 26, false, "Date: " + DATE.format(lrDate))
			.text(80, 270, 24, false, "Vehicle No.: " + vehicle)
			.text(820, 270, 24, false, "From: " + from + "   To: " + to)
			.box(60, 300, 1280, 320)
			.text(80, 345, 22, false, "Consignor: " + consignor)
			.text(80, 390, 22, false, "Consignee: " + consignee)
			.line(60, 415, 1340, 415)
			.text(80, 455, 21, true, "No. of Packages")
			.text(420, 455, 21, true, "Description (said to contain)")
			.text(1050, 455, 21, true, "Weight")
			.text(80, 505, 22, false, packages + " bags")
			.text(420, 505, 22, false, goods)
			.text(1050, 505, 22, false, (packages * 50 / 1000.0) + " MT")
			.text(80, 590, 19, false, "Freight: TO PAY / TBB as per agreement. Goods carried at owner's risk.")
			.text(80, 690, 22, true, "Received the goods in good order and condition:")
			.handwritten(140, 760, 30, remark)
			.handwritten(140, 820, 28, "Date " + DATE.format(deliveredOn))
			.signature(180, 900, 260)
			.text(140, 950, 19, false, "Receiver's signature")
			.stamp(1080, 820, 115, consignee.split(" ")[0].toUpperCase(Locale.ENGLISH), "RECEIVED",
					DATE.format(deliveredOn))
			.fill(0, 980, 1400, 20, new Color(225, 220, 205));
		return art.jpeg();
	}

}
