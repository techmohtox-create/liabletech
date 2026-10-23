package com.liabletech.platform.ai;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Builds the JSON schema for structured output from a result record.
 *
 * <p>
 * Every property is required and none is nullable: the API limits a schema to 24 optional and 16
 * union-typed properties, and the documents here have more fields than that. Text fields use ""
 * when a value is not on the document, which {@link com.liabletech.platform.text.Lenient#text}
 * turns back into null. Numbers that may be missing are read as text for the same reason.
 */
public final class JsonSchemas {

	private static final Map<Class<?>, Map<String, Object>> CACHE = new ConcurrentHashMap<>();

	private JsonSchemas() {
	}

	public static Map<String, Object> of(Class<?> type) {
		return CACHE.computeIfAbsent(type, JsonSchemas::object);
	}

	private static Map<String, Object> object(Class<?> type) {
		if (!type.isRecord()) {
			throw new IllegalArgumentException(type + " must be a record");
		}
		Map<String, Object> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();
		for (RecordComponent component : type.getRecordComponents()) {
			Map<String, Object> schema = new LinkedHashMap<>(schemaFor(component.getGenericType()));
			JsonPropertyDescription description = component.getAccessor().getAnnotation(JsonPropertyDescription.class);
			if (description == null && component.isAnnotationPresent(JsonPropertyDescription.class)) {
				description = component.getAnnotation(JsonPropertyDescription.class);
			}
			if (description != null) {
				schema.put("description", description.value());
			}
			properties.put(component.getName(), schema);
			required.add(component.getName());
		}
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		JsonClassDescription classDescription = type.getAnnotation(JsonClassDescription.class);
		if (classDescription != null) {
			schema.put("description", classDescription.value());
		}
		schema.put("properties", properties);
		schema.put("required", required);
		schema.put("additionalProperties", false);
		return schema;
	}

	private static Map<String, Object> schemaFor(Type type) {
		if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == List.class) {
			return Map.of("type", "array", "items", schemaFor(parameterized.getActualTypeArguments()[0]));
		}
		if (!(type instanceof Class<?> cls)) {
			throw new IllegalArgumentException("Unsupported type in AI result: " + type);
		}
		if (cls == String.class) {
			return Map.of("type", "string");
		}
		if (cls == boolean.class || cls == Boolean.class) {
			return Map.of("type", "boolean");
		}
		if (cls == int.class || cls == Integer.class || cls == long.class || cls == Long.class) {
			return Map.of("type", "integer");
		}
		if (cls == double.class || cls == Double.class || cls == float.class || cls == Float.class
				|| cls == BigDecimal.class) {
			return Map.of("type", "number");
		}
		if (cls.isEnum()) {
			return Map.of("type", "string", "enum",
					Arrays.stream(cls.getEnumConstants()).map((c) -> ((Enum<?>) c).name()).toList());
		}
		if (cls.isRecord()) {
			return object(cls);
		}
		throw new IllegalArgumentException("Unsupported type in AI result: " + cls);
	}

}
