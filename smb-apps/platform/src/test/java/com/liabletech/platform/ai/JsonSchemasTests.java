package com.liabletech.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

class JsonSchemasTests {

	enum Kind {

		CHALLAN, INVOICE

	}

	record Line(@JsonPropertyDescription("Material as written") String description, String quantity) {
	}

	record Reading(@JsonPropertyDescription("Challan number exactly as printed") String number, Kind kind,
			boolean stamped, double confidence, List<Line> lines) {
	}

	@Test
	@SuppressWarnings("unchecked")
	void describesARecordAsAStrictObjectSchema() {
		Map<String, Object> schema = JsonSchemas.of(Reading.class);

		assertThat(schema).containsEntry("type", "object").containsEntry("additionalProperties", false);
		assertThat((List<String>) schema.get("required")).containsExactly("number", "kind", "stamped", "confidence",
				"lines");
		Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
		assertThat((Map<String, Object>) properties.get("number")).containsEntry("type", "string")
			.containsEntry("description", "Challan number exactly as printed");
		assertThat((Map<String, Object>) properties.get("kind")).containsEntry("enum", List.of("CHALLAN", "INVOICE"));
		assertThat((Map<String, Object>) properties.get("stamped")).containsEntry("type", "boolean");
		assertThat((Map<String, Object>) properties.get("confidence")).containsEntry("type", "number");
		Map<String, Object> lines = (Map<String, Object>) properties.get("lines");
		assertThat(lines).containsEntry("type", "array");
		Map<String, Object> item = (Map<String, Object>) lines.get("items");
		assertThat(item).containsEntry("additionalProperties", false);
		assertThat((List<String>) item.get("required")).containsExactly("description", "quantity");
	}

}
