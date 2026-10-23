package com.liabletech.materials.catalog;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.liabletech.platform.text.Fuzzy;

/**
 * Works out which catalogue material a line on a slip or bill means: "UltraTech OPC 53 Grade",
 * "TMT Fe500D 12mm", "M-Sand (plaster)". Aliases do most of the work; for steel the bar diameter
 * must agree, so 12 mm is never mistaken for 16 mm.
 */
public final class MaterialMatcher {

	private static final Pattern DIAMETER = Pattern.compile("(?i)(\\d{1,2})\\s*(?:mm|m\\.m|ø|dia)");

	private MaterialMatcher() {
	}

	public static Optional<Material> match(String description, List<Material> materials) {
		if (description == null || description.isBlank()) {
			return Optional.empty();
		}
		String text = " " + Fuzzy.normalize(description) + " ";
		Integer diameter = diameter(description);
		long barSizes = materials.stream().filter((m) -> m.barDiameterMm() != null).count();
		Material best = null;
		double bestScore = 0;
		for (Material material : materials) {
			if (material.barDiameterMm() != null && diameter != null && !material.barDiameterMm().equals(diameter)) {
				continue;
			}
			// "TMT bars" with no size could be any of them: leave it for a person to pick.
			if (material.barDiameterMm() != null && diameter == null && barSizes > 1) {
				continue;
			}
			double score = score(text, material);
			if (material.barDiameterMm() != null && diameter != null) {
				score += 0.5;
			}
			if (score > bestScore) {
				bestScore = score;
				best = material;
			}
		}
		return bestScore >= 1 ? Optional.of(best) : Optional.empty();
	}

	private static double score(String text, Material material) {
		double score = 0;
		for (String alias : material.aliasList()) {
			String normalized = Fuzzy.normalize(alias);
			if (!normalized.isEmpty() && text.contains(" " + normalized + " ")) {
				score = Math.max(score, 1 + normalized.length() / 20.0);
			}
		}
		Set<String> nameWords = Arrays.stream(Fuzzy.normalize(material.name()).split(" "))
			.filter((w) -> w.length() > 2 && !w.equals("MM"))
			.collect(Collectors.toSet());
		long shared = nameWords.stream().filter((w) -> text.contains(" " + w + " ")).count();
		if (!nameWords.isEmpty() && shared == nameWords.size()) {
			score = Math.max(score, 1.2);
		}
		return score;
	}

	static Integer diameter(String description) {
		Matcher matcher = DIAMETER.matcher(description.toLowerCase(Locale.ROOT));
		return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
	}

}
