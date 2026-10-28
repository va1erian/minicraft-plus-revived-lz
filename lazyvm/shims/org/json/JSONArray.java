package org.json;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** LAZYVM: a small stand-in for org.json's JSONArray (see {@link JSONObject} for why). */
public class JSONArray implements Iterable<Object> {
	private final ArrayList<Object> list = new ArrayList<>();

	public JSONArray() {
	}

	public JSONArray(String source) {
		JSONText.Parser parser = new JSONText.Parser(source);
		parser.readArray(this);
		parser.end();
	}

	public JSONArray(Collection<?> collection) {
		if (collection != null) putAll(collection);
	}

	public JSONArray(Iterable<?> iterable) {
		if (iterable != null) for (Object o : iterable) put(JSONText.wrap(o));
	}

	/** Copies the elements of an array of any component type. */
	public JSONArray(Object array) {
		if (array == null || !array.getClass().isArray())
			throw new JSONException("JSONArray initial value should be a string or collection or array.");
		putAll(array);
	}

	public int length() {
		return list.size();
	}

	public boolean isEmpty() {
		return list.isEmpty();
	}

	public Object opt(int index) {
		return index < 0 || index >= list.size() ? null : list.get(index);
	}

	public Object get(int index) {
		Object value = opt(index);
		if (value == null) throw new JSONException("JSONArray[" + index + "] not found.");
		return value;
	}

	public String getString(int index) {
		Object value = get(index);
		if (value instanceof String) return (String) value;
		throw wrongType(index, "a string");
	}

	public String optString(int index) {
		Object value = opt(index);
		return JSONObject.NULL.equals(value) ? "" : value.toString();
	}

	public int getInt(int index) {
		return number(index).intValue();
	}

	public long getLong(int index) {
		return number(index).longValue();
	}

	public double getDouble(int index) {
		return number(index).doubleValue();
	}

	public boolean getBoolean(int index) {
		Boolean b = JSONText.toBoolean(get(index));
		if (b == null) throw wrongType(index, "a boolean");
		return b;
	}

	public JSONObject getJSONObject(int index) {
		Object value = get(index);
		if (value instanceof JSONObject) return (JSONObject) value;
		throw wrongType(index, "a JSONObject");
	}

	public JSONObject optJSONObject(int index) {
		Object value = opt(index);
		return value instanceof JSONObject ? (JSONObject) value : null;
	}

	public JSONArray getJSONArray(int index) {
		Object value = get(index);
		if (value instanceof JSONArray) return (JSONArray) value;
		throw wrongType(index, "a JSONArray");
	}

	public JSONArray optJSONArray(int index) {
		Object value = opt(index);
		return value instanceof JSONArray ? (JSONArray) value : null;
	}

	public JSONArray put(Object value) {
		list.add(value == null ? JSONObject.NULL : value);
		return this;
	}

	public JSONArray put(boolean value) {
		return put((Object) value);
	}

	public JSONArray put(int value) {
		return put((Object) value);
	}

	public JSONArray put(long value) {
		return put((Object) value);
	}

	public JSONArray put(double value) {
		return put((Object) value);
	}

	public JSONArray put(Collection<?> value) {
		return put((Object) new JSONArray(value));
	}

	public JSONArray put(Map<?, ?> value) {
		return put((Object) new JSONObject(value));
	}

	/** Sets the element at {@code index}, padding with nulls when it lies past the end. */
	public JSONArray put(int index, Object value) {
		if (index < 0) throw new JSONException("JSONArray[" + index + "] not found.");
		while (list.size() <= index) list.add(JSONObject.NULL);
		list.set(index, value == null ? JSONObject.NULL : value);
		return this;
	}

	public JSONArray putAll(Collection<?> collection) {
		for (Object o : collection) put(JSONText.wrap(o));
		return this;
	}

	public JSONArray putAll(Iterable<?> iterable) {
		for (Object o : iterable) put(JSONText.wrap(o));
		return this;
	}

	public JSONArray putAll(JSONArray array) {
		list.addAll(array.list);
		return this;
	}

	public JSONArray putAll(Object array) {
		if (array instanceof JSONArray) return putAll((JSONArray) array);
		if (array instanceof Collection) return putAll((Collection<?>) array);
		int n = Array.getLength(array);
		for (int i = 0; i < n; i++) put(JSONText.wrap(Array.get(array, i)));
		return this;
	}

	public Object remove(int index) {
		return index < 0 || index >= list.size() ? null : list.remove(index);
	}

	@Override
	public Iterator<Object> iterator() {
		return list.iterator();
	}

	/** The contents as plain lists and maps, with JSON nulls as null. */
	public List<Object> toList() {
		ArrayList<Object> out = new ArrayList<>(list.size());
		for (Object o : list) out.add(JSONText.unwrap(o));
		return out;
	}

	/** The elements' JSON texts joined by {@code separator}. */
	public String join(String separator) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) out.append(separator);
			JSONText.write(out, list.get(i), 0, 0);
		}
		return out.toString();
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentFactor) {
		StringBuilder out = new StringBuilder();
		JSONText.write(out, this, indentFactor, 0);
		return out.toString();
	}

	List<Object> elements() {
		return list;
	}

	private Number number(int index) {
		Number n = JSONText.toNumber(get(index));
		if (n == null) throw wrongType(index, "a number");
		return n;
	}

	private JSONException wrongType(int index, String type) {
		return new JSONException("JSONArray[" + index + "] is not " + type + ".");
	}
}
