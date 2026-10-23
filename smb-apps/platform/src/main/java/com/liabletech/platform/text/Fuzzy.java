package com.liabletech.platform.text;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Comparing names and codes that were read off photos: "M/s. Sharma Roadlines Pvt. Ltd." should
 * match "SHARMA ROADLINES PRIVATE LIMITED", and "LR-04521" should match "4521".
 */
public final class Fuzzy {

	private static final Set<String> NOISE_WORDS = Set.of("M", "S", "MS", "MESSRS", "THE", "AND", "OF", "PVT", "PRIVATE",
			"LTD", "LIMITED", "LLP", "CO", "COMPANY", "INC", "CORP", "CORPORATION");

	private Fuzzy() {
	}

	/** Upper case, accents and punctuation removed, single spaces. */
	public static String normalize(String text) {
		if (text == null) {
			return "";
		}
		String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return plain.toUpperCase(Locale.ROOT).replace('&', ' ').replaceAll("[^A-Z0-9]+", " ").trim();
	}

	/** Words that carry meaning in a business name, without "M/s", "Pvt", "Ltd" and the like. */
	public static Set<String> nameTokens(String text) {
		return Arrays.stream(normalize(text).split(" "))
			.filter(t -> !t.isEmpty() && !NOISE_WORDS.contains(t))
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/** Letters and digits only, for comparing reference numbers: "LR/04521" becomes "LR04521". */
	public static String code(String text) {
		return normalize(text).replace(" ", "");
	}

	/**
	 * Reference numbers match when equal after {@link #code}, or when one is the other with a
	 * letter prefix or leading zeros ("LR-04521" and "4521").
	 */
	public static boolean sameCode(String a, String b) {
		String x = code(a);
		String y = code(b);
		if (x.isEmpty() || y.isEmpty()) {
			return false;
		}
		if (x.equals(y)) {
			return true;
		}
		String dx = x.replaceFirst("^[A-Z]+", "").replaceFirst("^0+", "");
		String dy = y.replaceFirst("^[A-Z]+", "").replaceFirst("^0+", "");
		return !dx.isEmpty() && dx.equals(dy);
	}

	/**
	 * How alike two names are, from 0 (nothing in common) to 1 (same words). Blends word overlap with
	 * character similarity so that a misread letter ("SHARMA" vs "SHARNA") still scores high.
	 */
	public static double similarity(String a, String b) {
		Set<String> ta = nameTokens(a);
		Set<String> tb = nameTokens(b);
		if (ta.isEmpty() || tb.isEmpty()) {
			return 0;
		}
		String ja = String.join(" ", ta);
		String jb = String.join(" ", tb);
		if (ja.equals(jb)) {
			return 1;
		}
		long common = ta.stream().filter(tb::contains).count();
		double overlap = (double) common / Math.min(ta.size(), tb.size());
		double chars = 1 - (double) levenshtein(ja, jb) / Math.max(ja.length(), jb.length());
		return Math.max(chars, 0.6 * overlap + 0.4 * chars);
	}

	/** Share of the words in {@code required} that also appear in {@code text}, from 0 to 1. */
	public static double coverage(String required, String text) {
		Set<String> need = Arrays.stream(normalize(required).split(" "))
			.filter(t -> t.length() > 1)
			.collect(Collectors.toSet());
		if (need.isEmpty()) {
			return 1;
		}
		Set<String> have = Arrays.stream(normalize(text).split(" ")).collect(Collectors.toSet());
		return (double) need.stream().filter(have::contains).count() / need.size();
	}

	public static int levenshtein(String a, String b) {
		int[] previous = new int[b.length() + 1];
		int[] current = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			previous[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			current[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
			}
			int[] swap = previous;
			previous = current;
			current = swap;
		}
		return previous[b.length()];
	}

}
