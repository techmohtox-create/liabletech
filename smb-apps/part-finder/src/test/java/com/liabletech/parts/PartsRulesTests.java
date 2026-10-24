package com.liabletech.parts;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.liabletech.parts.catalog.Fitment;
import com.liabletech.parts.catalog.Part;
import com.liabletech.parts.catalog.PartType;
import com.liabletech.parts.catalog.StockCsv;
import com.liabletech.parts.catalog.VehicleCatalog;
import com.liabletech.parts.enquiry.Identification;
import com.liabletech.parts.enquiry.PartMatcher;
import com.liabletech.parts.enquiry.PartMessages;
import com.liabletech.parts.enquiry.QuoteOptions;
import com.liabletech.parts.enquiry.TextParser;
import com.liabletech.platform.account.Account;

/** How enquiries are read and matched, without a database. */
class PartsRulesTests {

	@Test
	void typedEnquiriesAreUnderstoodInEnglishAndHindi() {
		Identification pads = TextParser.parse("Swift 2019 front brake pad");
		assertThat(pads.partType()).isEqualTo(PartType.BRAKE_PAD);
		assertThat(pads.vehicleModel()).isEqualTo("Swift");
		assertThat(pads.vehicleMake()).isEqualTo("Maruti Suzuki");
		assertThat(pads.vehicleYear()).isEqualTo(2019);
		assertThat(pads.position()).isEqualTo("FRONT");
		assertThat(pads.question()).isNull();
		assertThat(pads.describe()).isEqualTo("Swift 2019 front brake pads");

		Identification clutch = TextParser.parse("bolero clutch plate chahiye");
		assertThat(clutch.partType()).isEqualTo(PartType.CLUTCH_PLATE);
		assertThat(clutch.vehicleModel()).isEqualTo("Bolero");

		assertThat(TextParser.parse("4 spark plug alto").quantity()).isEqualTo(4);
		assertThat(TextParser.parse("Alto 800 oil filter x4").quantity()).isEqualTo(4);
		assertThat(TextParser.parse("self motor nahi chal raha WagonR").partType()).isEqualTo(PartType.STARTER_MOTOR);
		Identification shocker = TextParser.parse("activa ka aage wala shocker");
		assertThat(shocker.partType()).isEqualTo(PartType.SHOCK_ABSORBER);
		assertThat(shocker.position()).isEqualTo("FRONT");
		assertThat(TextParser.parse("Dzire 19 model pads").vehicleYear()).isEqualTo(2019);
		assertThat(TextParser.parse("Dzire 2019 front pads").partType()).isEqualTo(PartType.BRAKE_PAD);
	}

	@Test
	void whatIsMissingBecomesTheQuestion() {
		assertThat(TextParser.parse("i20 brake pad hai kya?").question()).isEqualTo("Front or rear?");
		assertThat(TextParser.parse("clutch plate chahiye").question()).isEqualTo("Which vehicle and model year is it for?");
		assertThat(TextParser.parse("Swift ke liye").question()).isEqualTo("Which part do you need? A photo of the old part helps.");
		assertThat(TextParser.parse("Swift tie rod end").question()).isEqualTo("Left side or right side?");
		assertThat(TextParser.parse("hello ji").status()).isEqualTo(Identification.MANUAL);
	}

	@Test
	void numbersAreKeptButVehicleNamesAreNot() {
		assertThat(TextParser.parse("need 16510M68K00 filter").partNumbers()).containsExactly("16510M68K00");
		Identification xuv = TextParser.parse("XUV500 clutch plate");
		assertThat(xuv.partNumbers()).isEmpty();
		assertThat(xuv.vehicleModel()).isEqualTo("XUV500");
	}

	@Test
	void longestPartNameWins() {
		assertThat(PartType.find("clutch disc for swift")).contains(PartType.CLUTCH_PLATE);
		assertThat(PartType.find("brake disc")).contains(PartType.BRAKE_DISC);
		assertThat(PartType.find("release bearing")).contains(PartType.CLUTCH_BEARING);
		assertThat(PartType.find("front wheel bearing")).contains(PartType.WHEEL_BEARING);
		assertThat(PartType.find("dynamo")).contains(PartType.ALTERNATOR);
		assertThat(PartType.parse("BRAKE_PAD")).isEqualTo(PartType.BRAKE_PAD);
		assertThat(PartType.parse("Shocker")).isEqualTo(PartType.SHOCK_ABSORBER);
		assertThat(PartType.parse("flux capacitor")).isEqualTo(PartType.OTHER);
		assertThat(PartType.CLUTCH_PLATE.related()).containsExactly(PartType.CLUTCH_KIT);
	}

	@Test
	void vehiclesAreKnownByTheirUsualNames() {
		assertThat(VehicleCatalog.find("swift dzire 2018")).hasValueSatisfying((v) -> assertThat(v.model()).isEqualTo("Dzire"));
		assertThat(VehicleCatalog.canonical("DZIRE")).isEqualTo("Dzire");
		assertThat(VehicleCatalog.canonical("Kwid")).isEqualTo("Kwid");
		assertThat(VehicleCatalog.sameModel("Wagon R", "WagonR")).isTrue();
		assertThat(VehicleCatalog.find("please replace it")).as("'ace' only as a word").isEmpty();
	}

	@Test
	void fitmentsAreReadFromAStockList() {
		Fitment range = Fitment.parse("Swift 2018-2024");
		assertThat(range.model()).isEqualTo("Swift");
		assertThat(range.yearFrom()).isEqualTo(2018);
		assertThat(range.yearTo()).isEqualTo(2024);
		assertThat(range.fits("swift", 2020)).isTrue();
		assertThat(range.fits("Swift", 2017)).isFalse();
		assertThat(range.fits("Dzire", 2020)).isFalse();
		assertThat(range.describe()).isEqualTo("Swift 2018–2024");

		assertThat(Fitment.parse("Maruti Dzire 2017-")).satisfies((f) -> {
			assertThat(f.model()).isEqualTo("Dzire");
			assertThat(f.yearFrom()).isEqualTo(2017);
			assertThat(f.yearTo()).isNull();
		});
		assertThat(Fitment.parse("Bolero 2012 onwards").yearFrom()).isEqualTo(2012);
		assertThat(Fitment.parse("Bolero (diesel)").variant()).isEqualTo("diesel");
		assertThat(Fitment.parse("Swift 2019").yearTo()).isEqualTo(2019);
		assertThat(Fitment.parse("Alto").yearFrom()).isNull();
		assertThat(StockCsv.fitments("Swift 2018-2024; Dzire 2017-2024 | Bolero (diesel)")).hasSize(3);
	}

	@Test
	void numberOnThePartDecidesIt() {
		Part pads = part(1, "BP-SWF-PS", "Front brake pad set", PartType.BRAKE_PAD, "PS-1123", 6);
		Identification read = new Identification(PartType.OTHER, null, List.of("PS1123"), null, null, null, null, 1, 0.9,
				null, Identification.READ, null);

		PartMatcher.Candidate match = PartMatcher.match(read, List.of(pads), Map.of()).getFirst();

		assertThat(match.score()).isEqualTo(100);
		assertThat(match.sure()).isTrue();
	}

	@Test
	void typeVehicleAndSideMakeAMatchSure() {
		Part front = part(1, "BP-F", "Front brake pad set", PartType.BRAKE_PAD, null, 5);
		Part rear = part(2, "BS-R", "Rear brake pad set", PartType.BRAKE_PAD, null, 5);
		Part other = part(3, "BP-I20", "Front brake pad set", PartType.BRAKE_PAD, null, 5);
		Map<Long, List<Fitment>> fits = Map.of(1L, List.of(fit(1, "Swift", 2018, 2024)), 2L, List.of(fit(2, "Swift", 2018, 2024)),
				3L, List.of(fit(3, "i20", null, null)));

		List<PartMatcher.Candidate> found = PartMatcher.match(TextParser.parse("swift front brake pad"),
				List.of(front, rear, other), fits);
		assertThat(found).singleElement().satisfies((c) -> {
			assertThat(c.part().id()).isEqualTo(1L);
			assertThat(c.score()).isEqualTo(90);
		});

		assertThat(PartMatcher.match(TextParser.parse("swift brake pad"), List.of(front, rear), fits))
			.as("without the side, neither is sure")
			.allSatisfy((c) -> assertThat(c.sure()).isFalse());
		assertThat(PartMatcher.match(TextParser.parse("swift 2016 front brake pad"), List.of(front), fits))
			.singleElement()
			.satisfies((c) -> assertThat(c.score()).isEqualTo(55));
	}

	@Test
	void sideDoesNotMatterForAFilterAndAKitOnlyStandsIn() {
		Part filter = part(1, "OF-1", "Oil filter", PartType.OIL_FILTER, null, 40);
		Part kit = part(2, "CK-1", "Clutch kit", PartType.CLUTCH_KIT, null, 1);
		Map<Long, List<Fitment>> fits = Map.of(1L, List.of(fit(1, "Alto", null, null)), 2L,
				List.of(fit(2, "Bolero", 2011, 2024)));

		assertThat(PartMatcher.match(TextParser.parse("alto oil filter"), List.of(filter), fits).getFirst().score())
			.isEqualTo(90);
		assertThat(PartMatcher.match(TextParser.parse("bolero 2016 clutch plate"), List.of(kit), fits))
			.singleElement()
			.satisfies((c) -> {
				assertThat(c.sure()).as("never quoted without the counter").isFalse();
				assertThat(c.reasons()).contains("clutch kit instead");
			});
	}

	@Test
	void quoteListsTheOptionsAndHowToBook() {
		Account shop = new Account(1L, "Shree Balaji Auto Spares", "Owner", "+919000090000", "o@example.com", null, null,
				null, null, null, null, null, null, null, "PRO", null, OffsetDateTime.now());
		Identification asked = TextParser.parse("Swift 2019 oil filter 2 pcs");

		String quote = PartMessages.quote(shop, asked,
				List.of(new QuoteOptions.NewOption(1L, "Filtra oil filter", new BigDecimal("180")),
						new QuoteOptions.NewOption(2L, "Genuine oil filter", new BigDecimal("260"))),
				asked.quantity(), 15);

		assertThat(quote).startsWith("✅ Swift 2019 oil filter:")
			.contains("1) Filtra oil filter – ₹180 each (2 = ₹360)")
			.contains("2) Genuine oil filter – ₹260 each (2 = ₹520)")
			.contains("Reply 1 or 2 to book. Ready for pickup in 15 min.")
			.endsWith("– Shree Balaji Auto Spares");
		assertThat(PartMessages.acknowledge(shop, TextParser.parse("i20 brake pad"))).contains("i20 brake pads. Front or rear?");
	}

	@Test
	void csvValuesMayHoldCommasAndQuotes() {
		String csv = "﻿part_number,name\r\nCK-1,\"Clutch kit (plate, cover)\"\r\nX-2,\"Say \"\"hi\"\"\"\r\n";
		List<List<String>> rows = StockCsv.parse(csv.replace("﻿", ""));

		assertThat(rows).hasSize(3);
		assertThat(rows.get(1)).containsExactly("CK-1", "Clutch kit (plate, cover)");
		assertThat(rows.get(2)).containsExactly("X-2", "Say \"hi\"");
	}

	private static Part part(long id, String number, String name, PartType type, String alt, int stock) {
		return new Part(id, 1L, number, name, type.name(), "ProStop", alt, new BigDecimal("1000"), null, stock, "B1", true,
				OffsetDateTime.now(), OffsetDateTime.now());
	}

	private static Fitment fit(long partId, String model, Integer from, Integer to) {
		return new Fitment(null, partId, null, model, from, to, null);
	}

}
