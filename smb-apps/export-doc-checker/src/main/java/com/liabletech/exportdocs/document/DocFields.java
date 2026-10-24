package com.liabletech.exportdocs.document;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns any reading record into a form and back: its text fields as inputs, its item lines as a
 * table. The same review screen then serves every document type.
 */
public final class DocFields {

	/** Fields shown as multi-line text. */
	private static final Set<String> LONG = Set.of("goodsDescription", "documentsRequired", "additionalConditions",
			"clauses", "risks", "applicant", "beneficiary", "seller", "buyer", "consignee", "notifyParty", "shipper",
			"exporter", "issuingBank", "availableWith", "transportDetails", "marks", "insured");

	private static final Map<String, String> LABELS = Map.ofEntries(Map.entry("lcNumber", "LC number"),
			Map.entry("hsCode", "HS code"), Map.entry("netWeightKg", "Net weight (kg)"),
			Map.entry("grossWeightKg", "Gross weight (kg)"), Map.entry("measurementCbm", "Measurement (CBM)"),
			Map.entry("incotermPlace", "Trade term place"), Map.entry("incoterm", "Trade term (Incoterm)"),
			Map.entry("orderReference", "Proforma / PO reference"), Map.entry("onBoardDate", "On-board date"),
			Map.entry("shippedOnBoard", "Shipped on board (YES/NO)"), Map.entry("signed", "Signed (YES/NO)"),
			Map.entry("signedBy", "Signed as"), Map.entry("originalsIssued", "Originals issued"),
			Map.entry("tolerance", "Tolerance 39A (+/-%)"), Map.entry("documentsRequired", "Documents required (46A)"),
			Map.entry("additionalConditions", "Additional conditions (47A)"),
			Map.entry("goodsDescription", "Description of goods"), Map.entry("presentationPeriod", "Presentation period (48)"),
			Map.entry("latestShipmentDate", "Latest shipment date (44C)"), Map.entry("expiryDate", "Expiry date (31D)"),
			Map.entry("expiryPlace", "Expiry place (31D)"), Map.entry("issueDate", "Issue date"),
			Map.entry("portOfLoading", "Port of loading"), Map.entry("portOfDischarge", "Port of discharge"),
			Map.entry("partialShipments", "Partial shipments (43P)"), Map.entry("transhipment", "Transhipment (43T)"),
			Map.entry("availableWith", "Available with / by (41a)"), Map.entry("draftsAt", "Drafts at (42C)"),
			Map.entry("applicableRules", "Applicable rules (40E)"), Map.entry("form", "Form of credit (40A)"),
			Map.entry("amount", "Amount (32B)"), Map.entry("currency", "Currency"),
			Map.entry("applicant", "Applicant (50)"), Map.entry("beneficiary", "Beneficiary (59)"));

	private DocFields() {
	}

	public record Field(String name, String label, String value, boolean multiline, boolean date) {
	}

	public record Table(String name, List<String> columns, List<String> labels, List<Map<String, String>> rows) {
	}

	/** The record's text fields in declaration order; the confidence is not edited. */
	public static List<Field> fields(Record reading) {
		List<Field> fields = new ArrayList<>();
		for (RecordComponent component : reading.getClass().getRecordComponents()) {
			if (component.getType() != String.class) {
				continue;
			}
			Object value = Records.value(reading, component);
			String name = component.getName();
			fields.add(new Field(name, label(name), value == null ? "" : value.toString(), LONG.contains(name),
					name.endsWith("Date")));
		}
		return fields;
	}

	/** The record's item lines, if it has any, with blank rows to add more. */
	public static Table table(Record reading, int blankRows) {
		for (RecordComponent component : reading.getClass().getRecordComponents()) {
			if (component.getType() != List.class) {
				continue;
			}
			Class<?> lineType = lineType(component);
			List<String> columns = new ArrayList<>();
			for (RecordComponent column : lineType.getRecordComponents()) {
				columns.add(column.getName());
			}
			List<Map<String, String>> rows = new ArrayList<>();
			Object value = Records.value(reading, component);
			if (value instanceof List<?> list) {
				for (Object line : list) {
					Map<String, String> row = new LinkedHashMap<>();
					for (RecordComponent column : lineType.getRecordComponents()) {
						Object cell = Records.value((Record) line, column);
						row.put(column.getName(), cell == null ? "" : cell.toString());
					}
					rows.add(row);
				}
			}
			for (int i = 0; i < blankRows; i++) {
				Map<String, String> row = new LinkedHashMap<>();
				columns.forEach((c) -> row.put(c, ""));
				rows.add(row);
			}
			return new Table(component.getName(), columns, columns.stream().map(DocFields::label).toList(), rows);
		}
		return null;
	}

	/**
	 * A new reading from the review form: {@code f.<field>} for text fields and
	 * {@code l.<row>.<column>} for item lines. Rows left empty are dropped; the confidence is kept.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Record> T apply(Class<T> type, T current, Map<String, String> params) {
		RecordComponent[] components = type.getRecordComponents();
		Object[] values = new Object[components.length];
		for (int i = 0; i < components.length; i++) {
			RecordComponent component = components[i];
			if (component.getType() == String.class) {
				String value = params.get("f." + component.getName());
				values[i] = value == null ? Records.value(current, component) : value.strip();
			}
			else if (component.getType() == List.class) {
				Class<?> lineType = lineType(component);
				List<Object> lines = new ArrayList<>();
				int lastRow = params.keySet()
					.stream()
					.map((key) -> key.split("\\."))
					.filter((parts) -> parts.length == 3 && parts[0].equals("l") && parts[1].matches("\\d{1,3}"))
					.mapToInt((parts) -> Integer.parseInt(parts[1]))
					.max()
					.orElse(-1);
				for (int row = 0; row <= lastRow; row++) {
					String prefix = "l." + row + ".";
					RecordComponent[] columns = lineType.getRecordComponents();
					Object[] cells = new Object[columns.length];
					boolean empty = true;
					for (int c = 0; c < columns.length; c++) {
						String cell = params.getOrDefault(prefix + columns[c].getName(), "").strip();
						cells[c] = cell;
						empty &= cell.isEmpty();
					}
					if (!empty) {
						lines.add(Records.construct((Class<? extends Record>) lineType, cells));
					}
				}
				values[i] = List.copyOf(lines);
			}
			else {
				values[i] = Records.value(current, component);
			}
		}
		return Records.construct(type, values);
	}

	static String label(String name) {
		String known = LABELS.get(name);
		if (known != null) {
			return known;
		}
		String words = name.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	private static Class<?> lineType(RecordComponent component) {
		return (Class<?>) ((ParameterizedType) component.getGenericType()).getActualTypeArguments()[0];
	}

}
