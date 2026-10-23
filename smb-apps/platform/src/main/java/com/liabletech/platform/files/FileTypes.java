package com.liabletech.platform.files;

import java.util.Locale;
import java.util.Map;

/**
 * Works out what a file really is from its first bytes, so a renamed or mislabelled upload cannot
 * be served back as something else.
 */
public final class FileTypes {

	private static final Map<String, String> EXTENSIONS = Map.ofEntries(Map.entry("image/jpeg", ".jpg"),
			Map.entry("image/png", ".png"), Map.entry("image/gif", ".gif"), Map.entry("image/webp", ".webp"),
			Map.entry("application/pdf", ".pdf"), Map.entry("audio/ogg", ".ogg"), Map.entry("audio/mpeg", ".mp3"),
			Map.entry("audio/mp4", ".m4a"), Map.entry("audio/amr", ".amr"), Map.entry("audio/wav", ".wav"),
			Map.entry("audio/webm", ".webm"), Map.entry("text/plain", ".txt"), Map.entry("text/csv", ".csv"));

	private FileTypes() {
	}

	/**
	 * The content type from the file's signature, falling back to the declared type for text
	 * files. Returns null for anything the apps do not accept.
	 */
	public static String detect(byte[] bytes, String declared, String filename) {
		if (bytes.length >= 4) {
			if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
				return "image/jpeg";
			}
			if (startsWith(bytes, 0x89, 'P', 'N', 'G')) {
				return "image/png";
			}
			if (startsWith(bytes, 'G', 'I', 'F', '8')) {
				return "image/gif";
			}
			if (startsWith(bytes, '%', 'P', 'D', 'F')) {
				return "application/pdf";
			}
			if (startsWith(bytes, 'O', 'g', 'g', 'S')) {
				return "audio/ogg";
			}
			if (startsWith(bytes, '#', '!', 'A', 'M', 'R')) {
				return "audio/amr";
			}
			if (startsWith(bytes, 'I', 'D', '3') || startsWith(bytes, 0xFF, 0xFB) || startsWith(bytes, 0xFF, 0xF3)) {
				return "audio/mpeg";
			}
			if (startsWith(bytes, 0x1A, 0x45, 0xDF, 0xA3)) {
				return "audio/webm";
			}
			if (bytes.length >= 12 && startsWith(bytes, 'R', 'I', 'F', 'F')) {
				String kind = new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
				if (kind.equals("WEBP")) {
					return "image/webp";
				}
				if (kind.equals("WAVE")) {
					return "audio/wav";
				}
			}
			if (bytes.length >= 12 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p') {
				return "audio/mp4";
			}
		}
		String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
		if (name.endsWith(".csv") || "text/csv".equals(declared)) {
			return "text/csv";
		}
		if (name.endsWith(".txt") || (declared != null && declared.startsWith("text/plain"))) {
			return "text/plain";
		}
		return null;
	}

	public static String extension(String contentType) {
		return EXTENSIONS.getOrDefault(contentType, ".bin");
	}

	private static boolean startsWith(byte[] bytes, int... prefix) {
		if (bytes.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if ((bytes[i] & 0xFF) != prefix[i]) {
				return false;
			}
		}
		return true;
	}

}
