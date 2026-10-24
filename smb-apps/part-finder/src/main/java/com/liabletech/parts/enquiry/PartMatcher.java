package com.liabletech.parts.enquiry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.liabletech.parts.catalog.Fitment;
import com.liabletech.parts.catalog.Part;
import com.liabletech.parts.catalog.PartType;
import com.liabletech.platform.text.Fuzzy;

/**
 * Ranks the shop's parts against what was asked for. A number seen on the part or its box decides
 * it. Otherwise the type must match and the part must fit the vehicle, and the year and the side
 * (front or rear) add to the score or rule a part out.
 *
 * <p>
 * Scores: a number match is 100. The right type is 40, fitting the model 40 more, the right year
 * or side 10 each; a part that does not list the model, or is for the other side, is left out. Only
 * a score of 90 or more is sure enough to quote without a person looking.
 */
public final class PartMatcher {

	public static final int SURE = 90;

	private PartMatcher() {
	}

	public static List<Candidate> match(Identification wanted, List<Part> parts, Map<Long, List<Fitment>> fitments) {
		List<Candidate> candidates = new ArrayList<>();
		for (Part part : parts) {
			List<Fitment> fits = fitments.getOrDefault(part.id(), List.of());
			Candidate candidate = score(wanted, part, fits);
			if (candidate != null) {
				candidates.add(candidate);
			}
		}
		candidates.sort(Comparator.comparingInt(Candidate::score)
			.reversed()
			.thenComparing((Candidate c) -> !c.part().inStock())
			.thenComparing((Candidate c) -> c.part().price()));
		return candidates;
	}

	static Candidate score(Identification wanted, Part part, List<Fitment> fits) {
		List<String> reasons = new ArrayList<>();
		String fitText = String.join(", ", fits.stream().map(Fitment::describe).toList());
		if (numberMatches(wanted.partNumbers(), part)) {
			reasons.add("number matches");
			return new Candidate(part, 100, reasons, fitText);
		}
		PartType type = wanted.partType();
		if (type == null || type == PartType.OTHER) {
			return null;
		}
		boolean standIn = part.type() != type && type.related().contains(part.type());
		if (part.type() != type && !standIn) {
			return null;
		}
		int score = standIn ? 30 : 40;
		if (standIn) {
			reasons.add(part.type().label().toLowerCase() + " instead");
		}
		if (wanted.knowsVehicle()) {
			if (fits.isEmpty()) {
				reasons.add("fitment not listed");
			}
			else if (fits.stream().noneMatch((f) -> f.fits(wanted.vehicleModel(), null))) {
				return null;
			}
			else {
				score += 40;
				reasons.add("fits " + com.liabletech.parts.catalog.VehicleCatalog.canonical(wanted.vehicleModel()));
				if (wanted.vehicleYear() != null) {
					if (fits.stream().anyMatch((f) -> f.fits(wanted.vehicleModel(), wanted.vehicleYear()))) {
						score += 10;
						reasons.add(wanted.vehicleYear() + " fits");
					}
					else {
						score -= 25;
						reasons.add("not listed for " + wanted.vehicleYear());
					}
				}
			}
		}
		Side asked = Side.of(wanted.position());
		Side offered = Side.of(part.name());
		if (asked.conflicts(offered)) {
			return null;
		}
		boolean sided = TextParser.SIDED.contains(type);
		if (score >= 80 && (!sided || asked.sameAs(offered))) {
			score += 10;
			reasons.add(sided ? "right side" : "side does not matter");
		}
		return new Candidate(part, standIn ? Math.min(score, SURE - 10) : score, reasons, fitText);
	}

	private static boolean numberMatches(List<String> numbers, Part part) {
		for (String number : numbers) {
			String code = Fuzzy.code(number);
			if (code.length() < 4) {
				continue;
			}
			for (String own : part.numbers()) {
				String ownCode = Fuzzy.code(own);
				if (Fuzzy.sameCode(number, own) || (code.length() >= 6 && ownCode.contains(code))
						|| (ownCode.length() >= 6 && code.contains(ownCode))) {
					return true;
				}
			}
		}
		return false;
	}

	/** Front, rear, left or right as named in a request or a part's name. */
	record Side(boolean front, boolean rear, boolean left, boolean right) {

		static Side of(String text) {
			if (text == null) {
				return new Side(false, false, false, false);
			}
			String lower = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ") + " ";
			return new Side(lower.contains(" front "), lower.contains(" rear ") || lower.contains(" back "),
					lower.contains(" left ") || lower.contains(" lh "), lower.contains(" right ") || lower.contains(" rh "));
		}

		boolean any() {
			return front || rear || left || right;
		}

		/** Front asked and rear offered, or left and right. */
		boolean conflicts(Side other) {
			return front && other.rear || rear && other.front || left && other.right || right && other.left;
		}

		/** Both name the same side; nothing named on either is not the same. */
		boolean sameAs(Side other) {
			return front && other.front || rear && other.rear || left && other.left || right && other.right;
		}

	}

	/**
	 * A part that could be what was asked for.
	 *
	 * @param fits what the part fits, for the counter to check
	 */
	public record Candidate(Part part, int score, List<String> reasons, String fits) {

		public boolean sure() {
			return score >= SURE;
		}

	}

}
