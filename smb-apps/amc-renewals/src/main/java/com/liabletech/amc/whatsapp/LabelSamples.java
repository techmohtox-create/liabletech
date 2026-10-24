package com.liabletech.amc.whatsapp;

import java.awt.Color;

import com.liabletech.platform.samples.SampleArt;

/** Rating labels as a technician photographs them: an AC outdoor unit's plate and a purifier's sticker. */
public final class LabelSamples {

	private LabelSamples() {
	}

	public static byte[] splitAc(String brand, String model, String serial, String tonnage, String gas, String made) {
		SampleArt art = SampleArt.label(1200, 820, serial.hashCode());
		art.fill(0, 0, 1200, 110, new Color(28, 58, 110))
			.text(60, 75, 46, true, Color.WHITE, brand.toUpperCase())
			.text(760, 72, 26, false, Color.WHITE, "ROOM AIR CONDITIONER")
			.text(60, 175, 28, true, "OUTDOOR UNIT · SPLIT TYPE · INVERTER")
			.line(50, 200, 1150, 200)
			.text(60, 250, 26, false, "Model No.")
			.text(360, 250, 30, true, model)
			.text(60, 305, 26, false, "Serial No.")
			.text(360, 305, 30, true, serial)
			.text(60, 360, 26, false, "Cooling capacity")
			.text(360, 360, 26, true, tonnage + " (5200 W)")
			.text(60, 415, 26, false, "Refrigerant")
			.text(360, 415, 26, true, gas + "  0.95 kg")
			.text(60, 470, 26, false, "Power supply")
			.text(360, 470, 26, false, "230 V ~ 50 Hz, 1 Ph")
			.text(60, 525, 26, false, "Rated input")
			.text(360, 525, 26, false, "1480 W")
			.text(60, 580, 26, false, "Mfg. month/year")
			.text(360, 580, 26, true, made)
			.box(760, 250, 380, 330)
			.text(800, 320, 30, true, "5 STAR")
			.text(800, 370, 22, false, "BEE energy label")
			.text(800, 420, 22, false, "ISEER 5.0")
			.text(60, 680, 20, false, "Made in India. Keep 30 cm clearance on all sides.")
			.fill(0, 780, 1200, 40, new Color(205, 208, 212));
		return art.jpeg();
	}

	public static byte[] ro(String brand, String model, String serial, String made) {
		SampleArt art = SampleArt.label(1000, 760, serial.hashCode());
		art.fill(0, 0, 1000, 100, new Color(14, 116, 144))
			.text(50, 68, 42, true, Color.WHITE, brand.toUpperCase())
			.text(560, 66, 24, false, Color.WHITE, "RO + UV + UF PURIFIER")
			.text(50, 170, 26, false, "Model")
			.text(330, 170, 28, true, model)
			.text(50, 225, 26, false, "Sr. No.")
			.text(330, 225, 28, true, serial)
			.text(50, 280, 26, false, "Purification capacity")
			.text(330, 280, 26, true, "15 L/hr")
			.text(50, 335, 26, false, "Storage tank")
			.text(330, 335, 26, false, "8 L")
			.text(50, 390, 26, false, "Input voltage")
			.text(330, 390, 26, false, "100-300 V AC, 50 Hz")
			.text(50, 445, 26, false, "Mfd.")
			.text(330, 445, 26, true, made)
			.text(50, 540, 20, false, "Customer care: see warranty card. Do not open the cover; high voltage inside.")
			.fill(0, 720, 1000, 40, new Color(205, 208, 212));
		return art.jpeg();
	}

}
