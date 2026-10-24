package com.liabletech.parts.enquiry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.liabletech.parts.catalog.PartType;
import com.liabletech.parts.catalog.VehicleCatalog;

/**
 * Understands the usual typed enquiry without the AI: "Swift 2019 front brake pad", "bolero clutch
 * plate chahiye", "4 spark plug alto". What it cannot tell, it leaves for the AI or the counter.
 */
public final class TextParser {

	private static final Pattern YEAR = Pattern.compile("\\b(199\\d|20[0-4]\\d)\\b|'(\\d{2})\\b|\\b(\\d{2})\\s*model\\b");

	private static final Pattern QUANTITY = Pattern
		.compile("(?i)\\b(\\d{1,2})\\s*(?:pcs?|pieces?|nos?|sets?|x)\\b|\\bx\\s*(\\d{1,2})\\b");

	private static final Pattern LEADING_COUNT = Pattern.compile("^\\s*(\\d{1,2})\\s+[a-z]");

	private static final Pattern NUMBER = Pattern.compile("\\b(?=[A-Za-z0-9/-]*\\d)(?=[A-Za-z0-9/-]*[A-Za-z])[A-Za-z0-9][A-Za-z0-9/-]{4,}\\b");

	private static final Pattern FRONT = Pattern.compile("(?i)\\b(front|frnt|aage|aagey|agla|agle|aage\\s*ka)\\b");

	private static final Pattern REAR = Pattern.compile("(?i)\\b(rear|back|piche|peeche|pichla|pichle|peechhe)\\b");

	private static final Pattern LEFT = Pattern.compile("(?i)\\b(left|lh|l\\.h\\.)\\b");

	private static final Pattern RIGHT = Pattern.compile("(?i)\\b(right|rh|r\\.h\\.)\\b");

	/** Parts that come in front and rear (or left and right) versions. */
	static final Set<PartType> SIDED = Set.of(PartType.BRAKE_PAD, PartType.BRAKE_DISC, PartType.SHOCK_ABSORBER,
			PartType.WHEEL_BEARING, PartType.CONTROL_ARM, PartType.TIE_ROD_END, PartType.BALL_JOINT,
			PartType.STABILISER_LINK, PartType.CV_JOINT, PartType.ENGINE_MOUNT);

	private TextParser() {
	}

	public static Identification parse(String text) {
		if (text == null || text.isBlank()) {
			return Identification.nothing("No words to go on.");
		}
		Optional<PartType> type = PartType.find(text);
		Optional<VehicleCatalog.Vehicle> vehicle = VehicleCatalog.find(text);
		Integer year = year(text);
		String position = position(text);
		List<String> numbers = numbers(text);
		int quantity = quantity(text);
		if (type.isEmpty() && vehicle.isEmpty() && numbers.isEmpty()) {
			return Identification.nothing("No part or vehicle named.");
		}
		double confidence = type.isPresent() && vehicle.isPresent() ? 0.85 : !numbers.isEmpty() ? 0.7
				: type.isPresent() ? 0.5 : 0.3;
		return new Identification(type.orElse(null), type.map(PartType::label).orElse(null), numbers,
				vehicle.map(VehicleCatalog.Vehicle::make).orElse(null), vehicle.map(VehicleCatalog.Vehicle::model).orElse(null),
				year, position, quantity, confidence, question(type.orElse(null), vehicle.isPresent(), position, numbers),
				Identification.PARSED, null);
	}

	/** The one thing to ask, or null when the enquiry is clear enough to quote. */
	static String question(PartType type, boolean vehicleKnown, String position, List<String> numbers) {
		if (type == null && numbers.isEmpty()) {
			return "Which part do you need? A photo of the old part helps.";
		}
		if (!vehicleKnown && numbers.isEmpty()) {
			return "Which vehicle and model year is it for?";
		}
		if (type != null && SIDED.contains(type) && position == null && numbers.isEmpty()) {
			return type == PartType.TIE_ROD_END || type == PartType.CV_JOINT ? "Left side or right side?" : "Front or rear?";
		}
		return null;
	}

	static Integer year(String text) {
		Matcher matcher = YEAR.matcher(text.toLowerCase(Locale.ROOT));
		while (matcher.find()) {
			if (matcher.group(1) != null) {
				return Integer.valueOf(matcher.group(1));
			}
			String twoDigits = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
			int value = Integer.parseInt(twoDigits);
			if (value <= 40) {
				return 2000 + value;
			}
		}
		return null;
	}

	static String position(String text) {
		String side = FRONT.matcher(text).find() ? "FRONT" : REAR.matcher(text).find() ? "REAR" : null;
		String hand = LEFT.matcher(text).find() ? "LEFT" : RIGHT.matcher(text).find() ? "RIGHT" : null;
		if (side != null && hand != null) {
			return side + " " + hand;
		}
		return side != null ? side : hand;
	}

	static int quantity(String text) {
		Matcher matcher = QUANTITY.matcher(text);
		if (matcher.find()) {
			return Integer.parseInt(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
		}
		Matcher leading = LEADING_COUNT.matcher(text.toLowerCase(Locale.ROOT));
		if (leading.find()) {
			int value = Integer.parseInt(leading.group(1));
			return value >= 1 && value <= 20 ? value : 1;
		}
		return 1;
	}

	/** Codes with letters and digits that are not a vehicle's name ("XUV500") or a year range. */
	static List<String> numbers(String text) {
		Set<String> found = new LinkedHashSet<>();
		Matcher matcher = NUMBER.matcher(text);
		while (matcher.find()) {
			String token = matcher.group();
			if (VehicleCatalog.find(token).isPresent()) {
				continue;
			}
			found.add(token.toUpperCase(Locale.ROOT));
		}
		return new ArrayList<>(found);
	}

}
