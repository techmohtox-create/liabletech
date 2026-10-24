package com.liabletech.parts.whatsapp;

import java.awt.Color;

import com.liabletech.platform.samples.SampleArt;

/** Photos a mechanic sends: the end of a part's box, or the tag on an old part. */
public final class PartSamples {

	private PartSamples() {
	}

	/** The printed end of a part's box: brand, part, number and what it fits. */
	public static byte[] box(String brand, String part, String side, String number, String fits, Color band) {
		SampleArt art = SampleArt.label(1100, 700, number.hashCode());
		art.fill(0, 0, 1100, 120, band)
			.text(50, 82, 54, true, Color.WHITE, brand.toUpperCase())
			.text(640, 78, 26, false, Color.WHITE, "GENUINE QUALITY SPARES")
			.text(50, 210, 44, true, part.toUpperCase())
			.text(50, 270, 30, false, side)
			.line(40, 300, 1060, 300)
			.text(50, 360, 28, false, "Part No.")
			.text(250, 360, 34, true, number)
			.text(50, 420, 28, false, "Application")
			.text(250, 420, 28, true, fits)
			.text(50, 480, 24, false, "Qty: 1 set   ·   Made in India");
		for (int i = 0; i < 46; i++) {
			int width = (i * 7) % 3 + 2;
			art.fill(60 + i * 9, 540, width, 110, SampleArt.INK);
		}
		art.text(520, 600, 22, false, "Check fitment before installing");
		return art.jpeg();
	}

	/** A paper tag tied to an old part brought in, written by the mechanic. */
	public static byte[] tag(String line1, String line2, String line3) {
		SampleArt art = SampleArt.paper(900, 620, line1.hashCode());
		art.box(40, 40, 820, 540)
			.handwritten(90, 170, 54, line1)
			.handwritten(90, 300, 46, line2)
			.handwritten(90, 420, 40, line3);
		return art.jpeg();
	}

}
