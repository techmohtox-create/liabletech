package com.liabletech.exportdocs.check;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares ports as written on a credit and on shipping documents: "NHAVA SHEVA, INDIA", "JNPT" and
 * "Jawaharlal Nehru Port" are one port; "ANY INDIAN PORT" accepts every Indian port.
 */
public final class Ports {

	private static final Map<String, List<String>> ALIASES = Map.ofEntries(
			Map.entry("NHAVA SHEVA", List.of("NHAVA SHEVA", "NHAVASHEVA", "JNPT", "JAWAHARLAL NEHRU", "JNPA", "INNSA")),
			Map.entry("MUMBAI", List.of("MUMBAI", "BOMBAY", "INBOM")), Map.entry("MUNDRA", List.of("MUNDRA", "INMUN")),
			Map.entry("KANDLA", List.of("KANDLA", "DEENDAYAL", "INIXY")), Map.entry("PIPAVAV", List.of("PIPAVAV", "INPAV")),
			Map.entry("HAZIRA", List.of("HAZIRA", "INHZA")),
			Map.entry("CHENNAI", List.of("CHENNAI", "MADRAS", "INMAA")), Map.entry("KATTUPALLI", List.of("KATTUPALLI", "INKAT")),
			Map.entry("ENNORE", List.of("ENNORE", "KAMARAJAR", "INENR")),
			Map.entry("TUTICORIN", List.of("TUTICORIN", "THOOTHUKUDI", "CHIDAMBARANAR", "INTUT")),
			Map.entry("KOCHI", List.of("KOCHI", "COCHIN", "INCOK")), Map.entry("MANGALORE", List.of("MANGALORE", "MANGALURU", "INIXE")),
			Map.entry("VISAKHAPATNAM", List.of("VISAKHAPATNAM", "VIZAG", "VISAKHAPATANAM", "INVTZ")),
			Map.entry("KRISHNAPATNAM", List.of("KRISHNAPATNAM", "INKRI")), Map.entry("KOLKATA", List.of("KOLKATA", "CALCUTTA", "INCCU")),
			Map.entry("HALDIA", List.of("HALDIA", "INHAL")), Map.entry("PARADIP", List.of("PARADIP", "PARADEEP", "INPRT")),
			Map.entry("JEBEL ALI", List.of("JEBEL ALI", "JEBELALI", "AEJEA")), Map.entry("ROTTERDAM", List.of("ROTTERDAM", "NLRTM")),
			Map.entry("HAMBURG", List.of("HAMBURG", "DEHAM")), Map.entry("ANTWERP", List.of("ANTWERP", "ANTWERPEN", "BEANR")),
			Map.entry("NEW YORK", List.of("NEW YORK", "NEWARK", "USNYC")), Map.entry("FELIXSTOWE", List.of("FELIXSTOWE", "GBFXT")),
			Map.entry("SINGAPORE", List.of("SINGAPORE", "SGSIN")), Map.entry("COLOMBO", List.of("COLOMBO", "LKCMB")));

	private static final Set<String> INDIAN = Set.of("NHAVA SHEVA", "MUMBAI", "MUNDRA", "KANDLA", "PIPAVAV", "HAZIRA",
			"CHENNAI", "KATTUPALLI", "ENNORE", "TUTICORIN", "KOCHI", "MANGALORE", "VISAKHAPATNAM", "KRISHNAPATNAM", "KOLKATA",
			"HALDIA", "PARADIP");

	private Ports() {
	}

	public enum Match {

		SAME, DIFFERENT, UNKNOWN

	}

	/** Whether the port on a document satisfies the port the credit names. */
	public static Match compare(String required, String actual) {
		String want = " " + TradeText.normalize(required) + " ";
		String have = " " + TradeText.normalize(actual) + " ";
		if (want.isBlank() || have.isBlank()) {
			return Match.UNKNOWN;
		}
		if (want.startsWith(" ANY ")) {
			if (want.contains(" INDIA") && !want.contains(" INDIAN OCEAN")) {
				String port = canonical(have);
				if (port != null) {
					return INDIAN.contains(port) ? Match.SAME : Match.DIFFERENT;
				}
				return have.contains(" INDIA ") ? Match.SAME : Match.UNKNOWN;
			}
			return Match.UNKNOWN;
		}
		String a = canonical(want);
		String b = canonical(have);
		if (a != null && b != null) {
			return a.equals(b) ? Match.SAME : Match.DIFFERENT;
		}
		String first = firstWords(want);
		return have.contains(" " + first + " ") || want.contains(" " + firstWords(have) + " ") ? Match.SAME
				: Match.DIFFERENT;
	}

	static String canonical(String padded) {
		for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
			for (String alias : entry.getValue()) {
				if (padded.contains(" " + alias + " ")) {
					return entry.getKey();
				}
			}
		}
		return null;
	}

	/**
	 * The port's own name without the country: HAMBURG in "HAMBURG GERMANY". Short first words
	 * ("SAN", "NEW", "LOS") take the next word with them.
	 */
	private static String firstWords(String padded) {
		String text = padded.trim().replaceFirst("^(PORT OF|PORT) ", "");
		String[] words = text.split(" ");
		return words.length >= 2 && words[0].length() <= 3 ? words[0] + " " + words[1] : words[0];
	}

}
