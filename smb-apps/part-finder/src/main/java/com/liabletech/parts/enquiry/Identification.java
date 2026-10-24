package com.liabletech.parts.enquiry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.liabletech.parts.catalog.PartType;
import com.liabletech.parts.catalog.VehicleCatalog;

/**
 * What an enquiry asks for, however it was understood: by the AI, from the words alone, or as the
 * counter corrected it.
 *
 * @param status READ, PARSED, EDITED or MANUAL (nothing understood)
 */
public record Identification(PartType partType, String partName, List<String> partNumbers, String vehicleMake,
		String vehicleModel, Integer vehicleYear, String position, int quantity, double confidence, String question,
		String status, String note) {

	public static final String READ = "READ";

	public static final String PARSED = "PARSED";

	public static final String EDITED = "EDITED";

	public static final String MANUAL = "MANUAL";

	public static Identification nothing(String note) {
		return new Identification(null, null, List.of(), null, null, null, null, 1, 0, null, MANUAL, note);
	}

	public Identification withQuestion(String newQuestion) {
		return new Identification(partType, partName, partNumbers, vehicleMake, vehicleModel, vehicleYear, position, quantity,
				confidence, newQuestion, status, note);
	}

	public Identification withConfidence(double newConfidence) {
		return new Identification(partType, partName, partNumbers, vehicleMake, vehicleModel, vehicleYear, position, quantity,
				newConfidence, question, status, note);
	}

	public boolean knowsPart() {
		return partType != null && partType != PartType.OTHER || !partNumbers.isEmpty();
	}

	public boolean knowsVehicle() {
		return vehicleModel != null && !vehicleModel.isBlank();
	}

	/** "Swift 2019 front brake pads", "brake pads", or the part name when the type is unknown. */
	public String describe() {
		List<String> words = new ArrayList<>();
		if (knowsVehicle()) {
			words.add(VehicleCatalog.canonical(vehicleModel));
		}
		if (vehicleYear != null) {
			words.add(String.valueOf(vehicleYear));
		}
		if (position != null && !position.isBlank()) {
			words.add(position.toLowerCase(Locale.ROOT));
		}
		if (partType != null && partType != PartType.OTHER) {
			words.add(partType.label().toLowerCase(Locale.ROOT));
		}
		else if (partName != null && !partName.isBlank()) {
			words.add(partName);
		}
		else if (!partNumbers.isEmpty()) {
			words.add("part " + partNumbers.getFirst());
		}
		return words.isEmpty() ? "" : String.join(" ", words);
	}

}
