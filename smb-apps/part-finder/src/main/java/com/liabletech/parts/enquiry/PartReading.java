package com.liabletech.parts.enquiry;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** What the AI makes of a mechanic's photo or message: the part, the vehicle and any numbers on it. */
public record PartReading(
		@JsonPropertyDescription("One of BRAKE_PAD, BRAKE_SHOE, BRAKE_DISC, CLUTCH_PLATE, PRESSURE_PLATE, CLUTCH_KIT, "
				+ "CLUTCH_BEARING, OIL_FILTER, AIR_FILTER, FUEL_FILTER, CABIN_FILTER, SPARK_PLUG, WIPER_BLADE, BULB, HORN, "
				+ "BATTERY, STARTER_MOTOR, ALTERNATOR, RADIATOR, BELT, WHEEL_BEARING, SHOCK_ABSORBER, CONTROL_ARM, "
				+ "TIE_ROD_END, BALL_JOINT, STABILISER_LINK, CV_JOINT, ENGINE_MOUNT, CHAIN_SPROCKET, CABLE, WATER_PUMP, "
				+ "GASKET or OTHER") String partType,
		@JsonPropertyDescription("Short description of the part in English, e.g. \"Front disc brake pad set\"") String partName,
		@JsonPropertyDescription("Part numbers printed, stamped or on a sticker or box, exactly as shown; empty if none is "
				+ "readable. Never guess one.") List<String> partNumbers,
		@JsonPropertyDescription("Brand on the part or box, if shown") String brand,
		@JsonPropertyDescription("Vehicle maker, e.g. Maruti Suzuki, Hyundai, Mahindra, Hero, Honda") String vehicleMake,
		@JsonPropertyDescription("Vehicle model, e.g. Swift, Bolero, Splendor") String vehicleModel,
		@JsonPropertyDescription("Model year if stated or printed, e.g. \"2019\"; empty if unknown") String vehicleYear,
		@JsonPropertyDescription("FRONT, REAR, LEFT, RIGHT, FRONT LEFT and so on, if it matters for this part") String position,
		@JsonPropertyDescription("How many the mechanic wants, 1 if not said") int quantity,
		@JsonPropertyDescription("If the part or vehicle cannot be told for sure, the one question to ask the mechanic, in "
				+ "simple English, e.g. \"Please send a photo of the number on the part\"; empty otherwise") String question,
		@JsonPropertyDescription("Confidence from 0 to 1 that the part type and vehicle are right") double confidence) {

	static final String INSTRUCTIONS = """
			You help the counter of an Indian auto spare parts shop. A mechanic sent a photo of a part (often the old, \
			worn part or its box), or a message or voice-note transcript, saying what they need. Mechanics write in \
			English, Hindi or a mix ("Swift ka front brake pad", "Bolero clutch plate chahiye", "self motor Alto"). \
			Common words: shocker = shock absorber, self = starter motor, dynamo = alternator, liner = brake shoe.
			Identify the part type, the vehicle (from the message, the part's markings or the box), and copy any part \
			numbers exactly as they appear. Do not invent part numbers or years. If you cannot be sure of the part or \
			the vehicle, give your best reading with a low confidence and the one question that would settle it.""";

}
