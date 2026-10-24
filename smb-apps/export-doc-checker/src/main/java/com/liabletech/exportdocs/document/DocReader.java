package com.liabletech.exportdocs.document;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.liabletech.exportdocs.reading.DocClassifier;
import com.liabletech.exportdocs.reading.DocKindReading;
import com.liabletech.exportdocs.reading.LcReading;
import com.liabletech.exportdocs.reading.Mt700Parser;
import com.liabletech.exportdocs.reading.PdfText;
import com.liabletech.platform.ai.AiResult;
import com.liabletech.platform.ai.DocumentAi;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;

/**
 * Works out what a file is and reads it: a SWIFT credit from its text, everything else with Claude.
 * Without an API key a document is still stored, with an empty form to type the key fields into.
 */
@Service
public class DocReader {

	private final DocumentAi ai;

	private final FileStore files;

	public DocReader(DocumentAi ai, FileStore files) {
		this.ai = ai;
		this.files = files;
	}

	/**
	 * @param known the type the exporter chose when uploading, or null to work it out
	 * @param caption a WhatsApp caption, which may name the document ("BL draft")
	 */
	public Reading read(long accountId, DocType known, StoredFile file, String caption) {
		String text = file.isPdf() ? PdfText.extract(files.read(file)) : "";
		DocType type = known != null ? known : classify(accountId, file, caption, text);
		if (type.readingType() == null) {
			return new Reading(type, null, ShipmentDoc.MANUAL, "Kept with the shipment; not checked.", null);
		}
		if (type == DocType.LC && PdfText.hasText(text)) {
			Optional<LcReading> parsed = Mt700Parser.parse(text);
			if (parsed.isPresent()) {
				return new Reading(type, parsed.get(), ShipmentDoc.PARSED, "Read from the SWIFT MT700 text.",
						confidence(parsed.get().confidence()));
			}
		}
		AiResult<? extends Record> result = ai.read(new DocumentAi.Request<>(accountId, "export-" + type.name().toLowerCase(),
				type.instructions(), List.of(file), caption, type.readingType()));
		if (result.ok()) {
			return new Reading(type, result.value(), ShipmentDoc.READ, null, confidence(confidenceOf(result.value())));
		}
		String note = result.outcome() == AiResult.Outcome.DISABLED ? "Automatic reading is off: type in the key fields."
				: "Could not be read automatically (" + result.message() + "): type in the key fields.";
		return new Reading(type, Records.empty(type.readingType()), ShipmentDoc.MANUAL, note, null);
	}

	/** Text and captions first; the AI only when neither says what the document is. */
	DocType classify(long accountId, StoredFile file, String caption, String text) {
		Optional<DocType> type = DocClassifier.byHint(caption)
			.or(() -> DocClassifier.byText(text))
			.or(() -> DocClassifier.byHint(stripExtension(file.originalName())));
		if (type.isPresent()) {
			return type.get();
		}
		if (ai.available()) {
			AiResult<DocKindReading> kind = ai.read(new DocumentAi.Request<>(accountId, "export-kind",
					"Say which kind of export trade document this is.", List.of(file), caption, DocKindReading.class));
			if (kind.ok()) {
				DocType parsed = DocType.parse(kind.value().documentType());
				if (parsed != null) {
					return parsed;
				}
			}
		}
		return DocType.OTHER;
	}

	private static String stripExtension(String name) {
		if (name == null) {
			return null;
		}
		int dot = name.lastIndexOf('.');
		return (dot > 0 ? name.substring(0, dot) : name).replace('_', ' ').replace('-', ' ');
	}

	private static double confidenceOf(Record reading) {
		for (var component : reading.getClass().getRecordComponents()) {
			if (component.getName().equals("confidence") && component.getType() == double.class) {
				return (double) Records.value(reading, component);
			}
		}
		return 0;
	}

	private static BigDecimal confidence(double value) {
		return BigDecimal.valueOf(Math.max(0, Math.min(1, value))).setScale(2, RoundingMode.HALF_UP);
	}

	/** What a file turned out to be and what was read from it. */
	public record Reading(DocType type, Record data, String status, String note, BigDecimal confidence) {
	}

}
