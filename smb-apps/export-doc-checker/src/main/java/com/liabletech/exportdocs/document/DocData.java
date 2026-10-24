package com.liabletech.exportdocs.document;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Readings stored as JSON in {@code shipment_doc.data_json}, one record type per document type. */
@Component
public class DocData {

	private final ObjectMapper json;

	public DocData(ObjectMapper json) {
		this.json = json;
	}

	public String write(Record reading) {
		return reading == null ? null : json.writeValueAsString(reading);
	}

	/** The document's reading, or an empty one when it has none or it cannot be read back. */
	public Record read(ShipmentDoc doc) {
		DocType type = doc.type();
		if (type == null || type.readingType() == null) {
			return null;
		}
		if (doc.dataJson() == null || doc.dataJson().isBlank()) {
			return Records.empty(type.readingType());
		}
		try {
			return json.readValue(doc.dataJson(), type.readingType());
		}
		catch (JacksonException ex) {
			return Records.empty(type.readingType());
		}
	}

	/** The latest reading of each type, for a check. */
	public Map<DocType, Record> byType(List<ShipmentDoc> docs) {
		Map<DocType, Record> readings = new EnumMap<>(DocType.class);
		for (ShipmentDoc doc : docs) {
			Record reading = read(doc);
			if (reading != null) {
				readings.put(doc.type(), reading);
			}
		}
		return readings;
	}

}
