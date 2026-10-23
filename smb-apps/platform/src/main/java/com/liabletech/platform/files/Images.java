package com.liabletech.platform.files;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/** Resizing phone photos before they are sent for reading or embedded in a PDF. */
public final class Images {

	private Images() {
	}

	/**
	 * A JPEG no larger than {@code maxEdge} pixels on its long side. Phone cameras produce 4000-pixel
	 * photos of several megabytes; text on a receipt stays readable at 2000 pixels. Formats Java
	 * cannot decode (such as WebP) come back unchanged.
	 */
	public static Prepared fit(byte[] bytes, String contentType, int maxEdge) {
		BufferedImage image = decode(bytes);
		if (image == null) {
			return new Prepared(bytes, contentType, 0, 0);
		}
		int width = image.getWidth();
		int height = image.getHeight();
		double scale = Math.min(1.0, (double) maxEdge / Math.max(width, height));
		boolean small = scale == 1.0 && bytes.length < 1_500_000 && "image/jpeg".equals(contentType);
		if (small) {
			return new Prepared(bytes, contentType, width, height);
		}
		int newWidth = Math.max(1, (int) Math.round(width * scale));
		int newHeight = Math.max(1, (int) Math.round(height * scale));
		BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = scaled.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setColor(java.awt.Color.WHITE);
		g.fillRect(0, 0, newWidth, newHeight);
		g.drawImage(image, 0, 0, newWidth, newHeight, null);
		g.dispose();
		return new Prepared(jpeg(scaled, 0.88f), "image/jpeg", newWidth, newHeight);
	}

	public static BufferedImage decode(byte[] bytes) {
		try {
			return ImageIO.read(new ByteArrayInputStream(bytes));
		}
		catch (IOException ex) {
			return null;
		}
	}

	public static byte[] jpeg(BufferedImage image, float quality) {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(stream);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(quality);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		finally {
			writer.dispose();
		}
		return out.toByteArray();
	}

	public static byte[] png(BufferedImage image) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", out);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return out.toByteArray();
	}

	public record Prepared(byte[] bytes, String contentType, int width, int height) {
	}

}
