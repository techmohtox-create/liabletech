package com.liabletech.platform.ai;

import java.util.List;

import com.liabletech.platform.files.StoredFile;

/**
 * Reads photos and PDFs into typed records: a POD's consignment number, a challan's quantities, a
 * letter of credit's terms, an appliance label's serial number.
 *
 * <p>
 * Results are suggestions. Every app shows them on a review screen, and nothing that costs money
 * (an invoice, a dispute, a quote) happens on an unreviewed reading unless the owner turns that on.
 */
public interface DocumentAi {

	/** False when no API key is configured; documents then go straight to manual entry. */
	boolean available();

	<T> AiResult<T> read(Request<T> request);

	/**
	 * @param purpose a short label for the cost log, e.g. "pod" or "lc"
	 * @param instructions what the document is and how to read it; the fields themselves are
	 * described on the result record with {@code @JsonPropertyDescription}
	 * @param files photos (JPEG, PNG, WebP, GIF) and PDFs, in order
	 * @param text extra text to read alongside, such as a WhatsApp caption or a voice-note transcript
	 * @param resultType a record whose components define the JSON the model must return
	 */
	record Request<T>(Long accountId, String purpose, String instructions, List<StoredFile> files, String text,
			Class<T> resultType) {
	}

}
