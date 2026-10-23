package com.liabletech.platform.samples;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Random;

import com.liabletech.platform.files.Images;

/**
 * Draws stand-in documents for the WhatsApp simulator: a signed and stamped lorry receipt, a
 * delivery challan, an appliance label. They are clear enough for Claude to read, so the whole
 * photo-to-record flow can be tried locally without real paperwork.
 */
public final class SampleArt {

	private static final Font BASE = load("fonts/DejaVuSans.ttf");

	private static final Font BASE_BOLD = load("fonts/DejaVuSans-Bold.ttf");

	public static final Color INK = new Color(30, 30, 40);

	public static final Color BLUE_INK = new Color(25, 55, 150);

	public static final Color STAMP = new Color(40, 70, 170, 200);

	private final BufferedImage image;

	private final Graphics2D g;

	private final Random random;

	private SampleArt(int width, int height, Color paper, long seed) {
		this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		this.g = image.createGraphics();
		this.random = new Random(seed);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(paper);
		g.fillRect(0, 0, width, height);
		// faint speckle so it looks photographed rather than generated
		for (int i = 0; i < width * height / 400; i++) {
			int shade = 200 + random.nextInt(40);
			g.setColor(new Color(shade, shade, shade - 10, 40));
			g.fillRect(random.nextInt(width), random.nextInt(height), 2, 2);
		}
	}

	/** A sheet of off-white paper. */
	public static SampleArt paper(int width, int height, long seed) {
		return new SampleArt(width, height, new Color(250, 247, 238), seed);
	}

	/** A metallic or printed sticker background, for appliance rating labels. */
	public static SampleArt label(int width, int height, long seed) {
		return new SampleArt(width, height, new Color(232, 234, 236), seed);
	}

	public SampleArt text(int x, int y, float size, boolean bold, String text) {
		return text(x, y, size, bold, INK, text);
	}

	public SampleArt text(int x, int y, float size, boolean bold, Color color, String text) {
		g.setFont((bold ? BASE_BOLD : BASE).deriveFont(size));
		g.setColor(color);
		g.drawString(text, x, y);
		return this;
	}

	public SampleArt centered(int centerX, int y, float size, boolean bold, String text) {
		g.setFont((bold ? BASE_BOLD : BASE).deriveFont(size));
		int width = g.getFontMetrics().stringWidth(text);
		g.setColor(INK);
		g.drawString(text, centerX - width / 2, y);
		return this;
	}

	/** Slanted blue-ink text, like a figure filled in by hand. */
	public SampleArt handwritten(int x, int y, float size, String text) {
		AffineTransform shear = AffineTransform.getShearInstance(-0.25, 0);
		g.setFont(BASE.deriveFont(size).deriveFont(shear));
		g.setColor(BLUE_INK);
		g.drawString(text, x, y);
		return this;
	}

	public SampleArt line(int x1, int y1, int x2, int y2) {
		g.setColor(new Color(90, 90, 100));
		g.setStroke(new BasicStroke(1.4f));
		g.drawLine(x1, y1, x2, y2);
		return this;
	}

	public SampleArt box(int x, int y, int width, int height) {
		g.setColor(new Color(90, 90, 100));
		g.setStroke(new BasicStroke(1.6f));
		g.drawRect(x, y, width, height);
		return this;
	}

	/** A round rubber stamp with the text along its middle, slightly rotated. */
	public SampleArt stamp(int centerX, int centerY, int radius, String top, String middle, String bottom) {
		AffineTransform saved = g.getTransform();
		g.rotate(Math.toRadians(-12), centerX, centerY);
		g.setColor(STAMP);
		g.setStroke(new BasicStroke(4f));
		g.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
		g.setStroke(new BasicStroke(2f));
		g.drawOval(centerX - radius + 9, centerY - radius + 9, radius * 2 - 18, radius * 2 - 18);
		drawCentered(top, centerX, centerY - radius / 3, radius / 5f, true);
		drawCentered(middle, centerX, centerY + radius / 12, radius / 4.2f, true);
		drawCentered(bottom, centerX, centerY + radius / 2, radius / 6f, false);
		g.setTransform(saved);
		return this;
	}

	/** A signature-like scribble. */
	public SampleArt signature(int x, int y, int width) {
		Path2D path = new Path2D.Double();
		path.moveTo(x, y);
		double step = width / 14.0;
		for (int i = 1; i <= 14; i++) {
			double px = x + i * step;
			double py = y + (random.nextDouble() - 0.5) * 34 - (i % 3 == 0 ? 12 : 0);
			path.quadTo(px - step / 2, py - 18, px, py);
		}
		g.setColor(BLUE_INK);
		g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(path);
		return this;
	}

	public SampleArt fill(int x, int y, int width, int height, Color color) {
		g.setColor(color);
		g.fillRect(x, y, width, height);
		return this;
	}

	public Graphics2D graphics() {
		return g;
	}

	public byte[] jpeg() {
		g.dispose();
		return Images.jpeg(image, 0.9f);
	}

	public byte[] png() {
		g.dispose();
		return Images.png(image);
	}

	private void drawCentered(String text, int centerX, int y, float size, boolean bold) {
		if (text == null) {
			return;
		}
		g.setFont((bold ? BASE_BOLD : BASE).deriveFont(size));
		int width = g.getFontMetrics().stringWidth(text);
		g.drawString(text, centerX - width / 2, y);
	}

	private static Font load(String path) {
		try (InputStream in = SampleArt.class.getClassLoader().getResourceAsStream(path)) {
			return Font.createFont(Font.TRUETYPE_FONT, in);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		catch (FontFormatException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
