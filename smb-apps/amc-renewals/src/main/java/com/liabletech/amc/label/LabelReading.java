package com.liabletech.amc.label;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** What Claude reads off an appliance's rating label or nameplate. */
@JsonClassDescription("The rating label, nameplate or serial-number sticker of a home appliance")
public record LabelReading(
		@JsonPropertyDescription("SPLIT_AC, WINDOW_AC, CASSETTE_AC, RO (water purifier), WATER_HEATER (geyser), "
				+ "REFRIGERATOR, WASHING_MACHINE, CHIMNEY or OTHER") String applianceType,
		@JsonPropertyDescription("Brand, e.g. Voltas, LG, Daikin, Kent") String brand,
		@JsonPropertyDescription("Model number exactly as printed") String model,
		@JsonPropertyDescription("Serial number exactly as printed, letters and digits") String serialNumber,
		@JsonPropertyDescription("INDOOR or OUTDOOR for one unit of a split AC; \"\" otherwise") String unit,
		@JsonPropertyDescription("Capacity as printed: tonnes or kW cooling for an AC (e.g. 1.5 TR), litres per hour "
				+ "for a purifier, litres for a geyser or refrigerator") String capacity,
		@JsonPropertyDescription("Refrigerant gas, e.g. R32, R410A, R22") String refrigerant,
		@JsonPropertyDescription("Month and year of manufacture as printed, e.g. 03/2024") String manufactured,
		@JsonPropertyDescription("Energy star rating, e.g. 5 STAR") String starRating,
		@JsonPropertyDescription("Confidence from 0 to 1 that the fields are read correctly") double confidence) {

	static final String INSTRUCTIONS = """
			A photo a technician took of a home appliance's rating label, nameplate or serial sticker in India: an \
			air conditioner's indoor or outdoor unit, a water purifier, a geyser or another appliance. Labels are \
			small, often at an angle, dusty or reflecting light; read the model and serial number character by \
			character. Do not read the customer's details from the caption into the label fields.""";

}
