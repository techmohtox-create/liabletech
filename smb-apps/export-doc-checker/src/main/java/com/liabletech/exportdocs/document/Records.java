package com.liabletech.exportdocs.document;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

/** Reflection helpers for the reading records, which are all made of text fields, lists and a confidence. */
public final class Records {

	private Records() {
	}

	/** A reading with every text field empty: what a manually entered document starts from. */
	public static <T extends Record> T empty(Class<T> type) {
		RecordComponent[] components = type.getRecordComponents();
		Object[] values = new Object[components.length];
		for (int i = 0; i < components.length; i++) {
			values[i] = blank(components[i].getType());
		}
		return construct(type, values);
	}

	public static <T extends Record> T construct(Class<T> type, Object[] values) {
		try {
			Constructor<T> constructor = type.getDeclaredConstructor(
					Arrays.stream(type.getRecordComponents()).map(RecordComponent::getType).toArray(Class<?>[]::new));
			return constructor.newInstance(values);
		}
		catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
			throw new IllegalStateException("Cannot create " + type.getSimpleName(), ex);
		}
	}

	public static Object value(Record record, RecordComponent component) {
		try {
			return component.getAccessor().invoke(record);
		}
		catch (IllegalAccessException | InvocationTargetException ex) {
			throw new IllegalStateException("Cannot read " + component.getName(), ex);
		}
	}

	static Object blank(Class<?> type) {
		if (type == String.class) {
			return "";
		}
		if (type == List.class) {
			return List.of();
		}
		if (type == double.class) {
			return 0d;
		}
		if (type == boolean.class) {
			return false;
		}
		if (type == int.class) {
			return 0;
		}
		return null;
	}

}
