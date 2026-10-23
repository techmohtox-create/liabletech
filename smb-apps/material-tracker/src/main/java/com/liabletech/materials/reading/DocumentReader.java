package com.liabletech.materials.reading;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.liabletech.platform.ai.AiResult;
import com.liabletech.platform.ai.DocumentAi;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.text.Lenient;

/** Reads a photographed slip or bill and turns the text values into numbers, dates and units. */
@Service
public class DocumentReader {

	private final DocumentAi ai;

	public DocumentReader(DocumentAi ai) {
		this.ai = ai;
	}

	public Reading read(long accountId, StoredFile file, String caption) {
		AiResult<MaterialDocReading> result = ai.read(new DocumentAi.Request<>(accountId, "material-doc",
				MaterialDocReading.INSTRUCTIONS, List.of(file), caption, MaterialDocReading.class));
		if (!result.ok()) {
			return Reading.notRead(result.message());
		}
		MaterialDocReading doc = result.value();
		List<Line> lines = new ArrayList<>();
		for (MaterialDocReading.Line line : doc.lines() == null ? List.<MaterialDocReading.Line>of() : doc.lines()) {
			String description = Lenient.text(line.description());
			BigDecimal quantity = Lenient.number(line.quantity());
			if (description == null && quantity == null) {
				continue;
			}
			lines.add(new Line(description == null ? "(unreadable)" : description, quantity, Lenient.text(line.unit()),
					Lenient.number(line.rate()), Lenient.number(line.amount()), Lenient.text(line.challanReference())));
		}
		List<String> refs = doc.challanReferences() == null ? List.of()
				: doc.challanReferences().stream().map(Lenient::text).filter((r) -> r != null).toList();
		return new Reading(true, null, doc.documentType(), Lenient.text(doc.supplierName()),
				Lenient.text(doc.supplierGstin()), Lenient.text(doc.documentNumber()), Lenient.date(doc.documentDate()),
				Lenient.text(doc.vehicleNumber()), lines, refs, Lenient.number(doc.taxableAmount()),
				Lenient.number(doc.taxAmount()), Lenient.number(doc.totalAmount()),
				BigDecimal.valueOf(Math.max(0, Math.min(1, doc.confidence()))).setScale(2, java.math.RoundingMode.HALF_UP));
	}

	/** A reading with text turned into values; {@code read} is false when it has to be typed in. */
	public record Reading(boolean read, String note, MaterialDocReading.DocumentType documentType, String supplierName,
			String supplierGstin, String documentNumber, LocalDate documentDate, String vehicleNumber, List<Line> lines,
			List<String> challanReferences, BigDecimal taxableAmount, BigDecimal taxAmount, BigDecimal totalAmount,
			BigDecimal confidence) {

		static Reading notRead(String note) {
			return new Reading(false, note, null, null, null, null, null, null, List.of(), List.of(), null, null, null,
					null);
		}

		public boolean isBill() {
			return documentType == MaterialDocReading.DocumentType.TAX_INVOICE;
		}

	}

	public record Line(String description, BigDecimal quantity, String unit, BigDecimal rate, BigDecimal amount,
			String challanReference) {
	}

}
