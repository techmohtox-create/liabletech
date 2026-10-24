package com.liabletech.parts.catalog;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The kinds of part a counter sells, with the words mechanics use for them, in English and the
 * Hindi most of them type: "shocker" is a shock absorber, "self" the starter, "dynamo" the
 * alternator.
 */
public enum PartType {

	BRAKE_PAD("Brake pads", "brake pad", "brake pads", "disc pad", "disc pads", "brake ka pad", "pad set", "pads", "pad"),
	BRAKE_SHOE("Brake shoes", "brake shoe", "brake shoes", "brake liner", "liner", "brake lining"),
	BRAKE_DISC("Brake disc / drum", "brake disc", "disc rotor", "rotor", "brake drum", "drum"),
	CLUTCH_PLATE("Clutch plate", "clutch plate", "clutch plat", "clutch disc", "clutch disk"),
	PRESSURE_PLATE("Pressure plate", "pressure plate", "clutch cover"),
	CLUTCH_KIT("Clutch kit", "clutch kit", "clutch set", "clutch assembly"),
	CLUTCH_BEARING("Clutch bearing", "release bearing", "clutch bearing", "thrust bearing"),
	OIL_FILTER("Oil filter", "oil filter", "engine oil filter"),
	AIR_FILTER("Air filter", "air filter", "air cleaner"),
	FUEL_FILTER("Fuel filter", "fuel filter", "diesel filter", "petrol filter"),
	CABIN_FILTER("Cabin filter", "cabin filter", "ac filter", "a/c filter", "pollen filter"),
	SPARK_PLUG("Spark plug", "spark plug", "spark plugs", "plug"),
	WIPER_BLADE("Wiper blade", "wiper blade", "wiper"),
	BULB("Bulb", "headlight bulb", "head light bulb", "bulb"),
	HORN("Horn", "horn"),
	BATTERY("Battery", "battery"),
	STARTER_MOTOR("Starter motor", "starter motor", "self motor", "self starter", "starter", "self"),
	ALTERNATOR("Alternator", "alternator", "dynamo"),
	RADIATOR("Radiator", "radiator"),
	BELT("Belt", "fan belt", "timing belt", "alternator belt", "drive belt", "v belt", "belt"),
	WHEEL_BEARING("Wheel bearing", "wheel bearing", "hub bearing", "bearing"),
	SHOCK_ABSORBER("Shock absorber", "shock absorber", "shocker", "shockup", "strut", "shock"),
	CONTROL_ARM("Lower arm", "lower arm", "control arm", "suspension arm"),
	TIE_ROD_END("Tie rod end", "tie rod end", "tie rod", "rack end"),
	BALL_JOINT("Ball joint", "ball joint"),
	STABILISER_LINK("Stabiliser link", "stabiliser link", "stabilizer link", "link rod"),
	CV_JOINT("CV joint", "cv joint", "drive shaft", "axle"),
	ENGINE_MOUNT("Engine mount", "engine mount", "engine mounting", "mounting"),
	CHAIN_SPROCKET("Chain sprocket kit", "chain sprocket", "chain sprocket kit", "chain set", "chain kit"),
	CABLE("Cable", "clutch cable", "accelerator cable", "brake cable", "speedometer cable", "cable"),
	WATER_PUMP("Water pump", "water pump"),
	GASKET("Gasket", "head gasket", "gasket"),
	OTHER("Other part");

	private final String label;

	private final List<String> words;

	PartType(String label, String... words) {
		this.label = label;
		this.words = List.of(words);
	}

	public String label() {
		return label;
	}

	/** Parts that can stand in for this one, offered at the counter but never quoted automatically: a clutch kit holds the plate. */
	public java.util.Set<PartType> related() {
		return switch (this) {
			case CLUTCH_PLATE, PRESSURE_PLATE, CLUTCH_BEARING -> java.util.Set.of(CLUTCH_KIT);
			case CLUTCH_KIT -> java.util.Set.of(CLUTCH_PLATE, PRESSURE_PLATE);
			default -> java.util.Set.of();
		};
	}

	public List<String> words() {
		return words;
	}

	/** The type as written by the AI or in a CSV: "BRAKE_PAD", "brake pads", "Shocker". */
	public static PartType parse(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String constant = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z]+", "_");
		for (PartType type : values()) {
			if (type.name().equals(constant)) {
				return type;
			}
		}
		return find(value).orElse(OTHER);
	}

	/** The part named in free text; the longest phrase wins, so "clutch disc" is not a brake disc. */
	public static Optional<PartType> find(String text) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		String lower = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/]+", " ") + " ";
		return Arrays.stream(values())
			.flatMap((type) -> type.words.stream().map((word) -> new Hit(type, word, position(lower, word))))
			.filter((hit) -> hit.at >= 0)
			.max(Comparator.comparingInt((Hit hit) -> hit.word.length()).thenComparing((hit) -> -hit.at))
			.map((hit) -> hit.type);
	}

	private static int position(String text, String word) {
		Matcher matcher = Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text);
		return matcher.find() ? matcher.start() : -1;
	}

	private record Hit(PartType type, String word, int at) {
	}

}
