package com.liabletech.parts.enquiry;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.liabletech.parts.catalog.PartType;
import com.liabletech.parts.catalog.VehicleCatalog;
import com.liabletech.parts.voice.SpeechToText;
import com.liabletech.platform.ai.AiResult;
import com.liabletech.platform.ai.DocumentAi;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.text.Lenient;

/**
 * Works out what a mechanic is asking for. A clear typed request ("Swift 2019 front brake pad") is
 * understood from the words alone; photos and unclear words go to the AI when there is a key; voice
 * notes are transcribed first when a speech-to-text service is set up.
 */
@Service
public class Identifier {

	private final DocumentAi ai;

	private final SpeechToText speech;

	private final FileStore files;

	public Identifier(DocumentAi ai, SpeechToText speech, FileStore files) {
		this.ai = ai;
		this.speech = speech;
		this.files = files;
	}

	public Result identify(long accountId, String kind, StoredFile file, String text) {
		if (Enquiry.VOICE.equals(kind)) {
			if (file == null || !speech.available()) {
				return new Result(Identification.nothing("Voice note: play it at the counter."), null);
			}
			String transcript = speech.transcribe(file, files.read(file)).orElse(null);
			if (transcript == null) {
				return new Result(Identification.nothing("The voice note could not be transcribed; play it at the counter."),
						null);
			}
			String words = StringUtils.hasText(text) ? text + "\n" + transcript : transcript;
			return new Result(fromWords(accountId, words), transcript);
		}
		if (Enquiry.PHOTO.equals(kind) && file != null) {
			Identification fromCaption = TextParser.parse(text);
			if (!ai.available()) {
				return new Result(fromCaption.knowsPart() ? fromCaption
						: Identification.nothing("Photo: look at it at the counter (automatic reading is off)."), null);
			}
			AiResult<PartReading> result = ai.read(new DocumentAi.Request<>(accountId, "part-photo", PartReading.INSTRUCTIONS,
					List.of(file), text, PartReading.class));
			if (!result.ok()) {
				return new Result(fromCaption.knowsPart() ? fromCaption
						: Identification.nothing("The photo could not be read: " + result.message()), null);
			}
			return new Result(merge(from(result.value()), fromCaption), null);
		}
		return new Result(fromWords(accountId, text), null);
	}

	/** The words decide when they name both the part and the vehicle; otherwise the AI tries. */
	private Identification fromWords(long accountId, String text) {
		Identification parsed = TextParser.parse(text);
		if (parsed.knowsPart() && parsed.knowsVehicle() || !ai.available() || !StringUtils.hasText(text)) {
			return parsed;
		}
		AiResult<PartReading> result = ai.read(new DocumentAi.Request<>(accountId, "part-text", PartReading.INSTRUCTIONS,
				List.of(), text, PartReading.class));
		return result.ok() ? merge(from(result.value()), parsed) : parsed;
	}

	static Identification from(PartReading reading) {
		PartType type = PartType.parse(reading.partType());
		List<String> numbers = reading.partNumbers() == null ? List.of()
				: reading.partNumbers()
					.stream()
					.map(Lenient::text)
					.filter(Objects::nonNull)
					.filter((n) -> n.chars().anyMatch(Character::isDigit))
					.map((n) -> n.toUpperCase(Locale.ROOT))
					.distinct()
					.toList();
		String model = Lenient.text(reading.vehicleModel());
		String position = reading.position() == null ? null : TextParser.position(reading.position());
		return new Identification(type, Lenient.text(reading.partName()), numbers, Lenient.text(reading.vehicleMake()),
				model == null ? null : VehicleCatalog.canonical(model),
				reading.vehicleYear() == null ? null : TextParser.year(reading.vehicleYear()), position,
				Math.max(1, reading.quantity()), Math.max(0, Math.min(1, reading.confidence())), Lenient.text(reading.question()),
				Identification.READ, null);
	}

	/** The AI's reading, with anything it left out taken from the mechanic's own words. */
	static Identification merge(Identification read, Identification words) {
		boolean typeFromWords = (read.partType() == null || read.partType() == PartType.OTHER) && words.partType() != null;
		return new Identification(typeFromWords ? words.partType() : read.partType(),
				read.partName() != null ? read.partName() : words.partName(),
				read.partNumbers().isEmpty() ? words.partNumbers() : read.partNumbers(),
				read.vehicleModel() != null ? read.vehicleMake() : words.vehicleMake(),
				read.vehicleModel() != null ? read.vehicleModel() : words.vehicleModel(),
				read.vehicleYear() != null ? read.vehicleYear() : words.vehicleYear(),
				read.position() != null ? read.position() : words.position(),
				read.quantity() > 1 ? read.quantity() : words.quantity(), read.confidence(),
				read.question() != null && (read.vehicleModel() != null || words.vehicleModel() == null) ? read.question()
						: null,
				read.status(), read.note());
	}

	/**
	 * @param transcript the voice note's words, when it was transcribed
	 */
	public record Result(Identification identification, String transcript) {
	}

}
