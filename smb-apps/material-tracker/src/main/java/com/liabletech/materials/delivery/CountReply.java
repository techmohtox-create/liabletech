package com.liabletech.materials.delivery;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a supervisor's WhatsApp reply about what arrived: "OK" for everything as on the slip, or
 * line numbers with counted quantities: "1=195", "1-195, 2=1.2", or just "195" when the slip has
 * one line.
 */
public record CountReply(boolean allAsSlip, Map<Integer, BigDecimal> counts) {

	private static final Set<String> YES = Set.of("ok", "okay", "k", "yes", "y", "received", "all ok", "all received",
			"done", "haan", "ha", "han", "ho", "theek", "thik", "barabar", "sahi", "✅", "👍", "👌");

	private static final Pattern PAIR = Pattern.compile("(\\d{1,2})\\s*[=:\\-]\\s*(\\d+(?:\\.\\d+)?)");

	private static final Pattern SINGLE = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*[a-zA-Z.]*\\s*$");

	/** The reply as counts, or empty when it is not about quantities at all. */
	public static Optional<CountReply> parse(String text, int lineCount) {
		if (text == null) {
			return Optional.empty();
		}
		String cleaned = text.trim().toLowerCase(Locale.ROOT).replaceAll("[!.]+$", "");
		if (YES.contains(cleaned)) {
			return Optional.of(new CountReply(true, Map.of()));
		}
		Map<Integer, BigDecimal> counts = new LinkedHashMap<>();
		Matcher pairs = PAIR.matcher(cleaned);
		while (pairs.find()) {
			int line = Integer.parseInt(pairs.group(1));
			if (line >= 1 && line <= lineCount) {
				counts.put(line, new BigDecimal(pairs.group(2)));
			}
		}
		if (!counts.isEmpty()) {
			return Optional.of(new CountReply(false, counts));
		}
		Matcher single = SINGLE.matcher(cleaned);
		if (lineCount == 1 && single.matches()) {
			return Optional.of(new CountReply(false, Map.of(1, new BigDecimal(single.group(1)))));
		}
		return Optional.empty();
	}

}
