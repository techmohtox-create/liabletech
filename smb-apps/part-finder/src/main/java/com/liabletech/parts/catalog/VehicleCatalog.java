package com.liabletech.parts.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The cars, bikes and light commercials Indian counters sell for most, and how mechanics name them
 * ("Dzire", "desire", "Swift Dzire"; "WagonR", "wagon r"). Fitments in the stock list and what a
 * mechanic writes are both brought to the same model name before they are compared.
 */
public final class VehicleCatalog {

	private static final List<Vehicle> VEHICLES = new ArrayList<>();

	static {
		add("Maruti Suzuki", "Swift", "swift");
		add("Maruti Suzuki", "Dzire", "dzire", "swift dzire", "desire", "dezire");
		add("Maruti Suzuki", "Alto", "alto", "alto 800", "alto k10", "k10");
		add("Maruti Suzuki", "WagonR", "wagonr", "wagon r", "wagan r");
		add("Maruti Suzuki", "Baleno", "baleno");
		add("Maruti Suzuki", "Ertiga", "ertiga");
		add("Maruti Suzuki", "Brezza", "brezza", "vitara brezza");
		add("Maruti Suzuki", "Celerio", "celerio");
		add("Maruti Suzuki", "Eeco", "eeco", "omni");
		add("Maruti Suzuki", "Ciaz", "ciaz");
		add("Hyundai", "i10", "i10", "grand i10", "i 10");
		add("Hyundai", "i20", "i20", "elite i20", "i 20");
		add("Hyundai", "Creta", "creta");
		add("Hyundai", "Verna", "verna");
		add("Hyundai", "Santro", "santro");
		add("Hyundai", "Venue", "venue");
		add("Tata", "Nexon", "nexon");
		add("Tata", "Tiago", "tiago");
		add("Tata", "Punch", "punch");
		add("Tata", "Indica", "indica");
		add("Tata", "Ace", "tata ace", "chota hathi", "ace");
		add("Mahindra", "Bolero", "bolero", "bolero pickup", "bolero neo");
		add("Mahindra", "Scorpio", "scorpio", "scorpio n");
		add("Mahindra", "XUV500", "xuv500", "xuv 500");
		add("Mahindra", "Thar", "thar");
		add("Toyota", "Innova", "innova", "innova crysta", "crysta");
		add("Toyota", "Fortuner", "fortuner");
		add("Toyota", "Etios", "etios");
		add("Honda", "City", "honda city", "city");
		add("Honda", "Amaze", "amaze");
		add("Honda", "Jazz", "jazz");
		add("Hero", "Splendor", "splendor", "splendor plus", "splender");
		add("Hero", "HF Deluxe", "hf deluxe", "hf delux");
		add("Hero", "Passion", "passion");
		add("Honda", "Activa", "activa");
		add("Honda", "Shine", "shine", "cb shine");
		add("Bajaj", "Pulsar", "pulsar");
		add("Bajaj", "Platina", "platina");
		add("TVS", "Apache", "apache");
		add("TVS", "Jupiter", "jupiter");
		add("Royal Enfield", "Classic 350", "classic 350", "bullet", "classic");
	}

	private VehicleCatalog() {
	}

	private static void add(String make, String model, String... aliases) {
		VEHICLES.add(new Vehicle(make, model, List.of(aliases)));
	}

	/** The vehicle named in free text; the longest name wins ("swift dzire" is a Dzire). */
	public static Optional<Vehicle> find(String text) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		String lower = clean(text);
		Vehicle best = null;
		int bestLength = 0;
		for (Vehicle vehicle : VEHICLES) {
			for (String alias : vehicle.aliases()) {
				if (alias.length() > bestLength && contains(lower, alias)) {
					best = vehicle;
					bestLength = alias.length();
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/** The catalogue's name for a model, so "Swift Dzire" and "DZIRE" compare equal; unknown names as written. */
	public static String canonical(String model) {
		if (model == null || model.isBlank()) {
			return null;
		}
		String lower = clean(model);
		return VEHICLES.stream()
			.filter((v) -> v.aliases().contains(lower) || v.model().equalsIgnoreCase(model.trim()))
			.findFirst()
			.map(Vehicle::model)
			.or(() -> find(model).map(Vehicle::model))
			.orElse(model.trim());
	}

	public static boolean sameModel(String a, String b) {
		String x = canonical(a);
		String y = canonical(b);
		return x != null && y != null && x.equalsIgnoreCase(y);
	}

	/** Every model the catalogue knows, for the stock form's suggestions. */
	public static List<Vehicle> all() {
		return VEHICLES.stream().sorted(Comparator.comparing(Vehicle::make).thenComparing(Vehicle::model)).toList();
	}

	private static String clean(String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	private static boolean contains(String text, String alias) {
		Matcher matcher = Pattern.compile("(^|\\s)" + Pattern.quote(alias) + "($|\\s)").matcher(text);
		return matcher.find();
	}

	public record Vehicle(String make, String model, List<String> aliases) {

		public String display() {
			return make.equals("Maruti Suzuki") ? "Maruti " + model : make + " " + model;
		}

	}

}
