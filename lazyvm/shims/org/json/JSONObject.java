package org.json;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * LAZYVM: a small stand-in for org.json's JSONObject, written for the port with the members the
 * game uses. The real json-20220320.jar cannot run on LazyVM: it parses every number through
 * java.math.BigInteger or BigDecimal, and LazyVM has no java.math. Here integers become Integer
 * or Long and other numbers Double. Like org.json, keys are unordered, a duplicate key is an
 * error, and the getters accept numbers and booleans written as strings.
 */
public class JSONObject {
	/** The value of a JSON null, so that "present but null" differs from "absent". */
	public static final Object NULL = new Object() {
		@Override
		public boolean equals(Object o) {
			return o == null || o == this;
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public String toString() {
			return "null";
		}
	};

	private final HashMap<String, Object> map = new HashMap<>();

	public JSONObject() {
	}

	public JSONObject(String source) {
		JSONText.Parser parser = new JSONText.Parser(source);
		parser.readObject(this);
		parser.end();
	}

	public JSONObject(Map<?, ?> map) {
		for (Map.Entry<?, ?> e : map.entrySet())
			if (e.getValue() != null) put(String.valueOf(e.getKey()), JSONText.wrap(e.getValue()));
	}

	public Object opt(String key) {
		return key == null ? null : map.get(key);
	}

	public Object get(String key) {
		Object value = opt(key);
		if (value == null) throw new JSONException("JSONObject[" + quote(key) + "] not found.");
		return value;
	}

	public boolean has(String key) {
		return map.containsKey(key);
	}

	public boolean isNull(String key) {
		return NULL.equals(opt(key));
	}

	public Set<String> keySet() {
		return map.keySet();
	}

	public Iterator<String> keys() {
		return map.keySet().iterator();
	}

	public int length() {
		return map.size();
	}

	public boolean isEmpty() {
		return map.isEmpty();
	}

	public String getString(String key) {
		Object value = get(key);
		if (value instanceof String) return (String) value;
		throw wrongType(key, "a string");
	}

	public String optString(String key) {
		return optString(key, "");
	}

	public String optString(String key, String defaultValue) {
		Object value = opt(key);
		return NULL.equals(value) ? defaultValue : value.toString();
	}

	public int getInt(String key) {
		return number(key).intValue();
	}

	public int optInt(String key) {
		return optInt(key, 0);
	}

	public int optInt(String key, int defaultValue) {
		Number n = JSONText.toNumber(opt(key));
		return n == null ? defaultValue : n.intValue();
	}

	public long getLong(String key) {
		return number(key).longValue();
	}

	public long optLong(String key, long defaultValue) {
		Number n = JSONText.toNumber(opt(key));
		return n == null ? defaultValue : n.longValue();
	}

	public double getDouble(String key) {
		return number(key).doubleValue();
	}

	public double optDouble(String key, double defaultValue) {
		Number n = JSONText.toNumber(opt(key));
		return n == null ? defaultValue : n.doubleValue();
	}

	public float getFloat(String key) {
		return number(key).floatValue();
	}

	public boolean getBoolean(String key) {
		Boolean b = JSONText.toBoolean(get(key));
		if (b == null) throw wrongType(key, "a boolean");
		return b;
	}

	public boolean optBoolean(String key) {
		return optBoolean(key, false);
	}

	public boolean optBoolean(String key, boolean defaultValue) {
		Boolean b = JSONText.toBoolean(opt(key));
		return b == null ? defaultValue : b;
	}

	public JSONObject getJSONObject(String key) {
		Object value = get(key);
		if (value instanceof JSONObject) return (JSONObject) value;
		throw wrongType(key, "a JSONObject");
	}

	public JSONObject optJSONObject(String key) {
		Object value = opt(key);
		return value instanceof JSONObject ? (JSONObject) value : null;
	}

	public JSONArray getJSONArray(String key) {
		Object value = get(key);
		if (value instanceof JSONArray) return (JSONArray) value;
		throw wrongType(key, "a JSONArray");
	}

	public JSONArray optJSONArray(String key) {
		Object value = opt(key);
		return value instanceof JSONArray ? (JSONArray) value : null;
	}

	/** Stores {@code value} under {@code key}; a null value removes the key. */
	public JSONObject put(String key, Object value) {
		if (key == null) throw new NullPointerException("Null key.");
		if (value == null) map.remove(key);
		else map.put(key, value);
		return this;
	}

	public JSONObject put(String key, boolean value) {
		return put(key, (Object) value);
	}

	public JSONObject put(String key, int value) {
		return put(key, (Object) value);
	}

	public JSONObject put(String key, long value) {
		return put(key, (Object) value);
	}

	public JSONObject put(String key, double value) {
		return put(key, (Object) value);
	}

	public JSONObject put(String key, float value) {
		return put(key, (Object) value);
	}

	public JSONObject put(String key, Collection<?> value) {
		return put(key, (Object) new JSONArray(value));
	}

	public JSONObject put(String key, Map<?, ?> value) {
		return put(key, (Object) new JSONObject(value));
	}

	public Object remove(String key) {
		return map.remove(key);
	}

	/** The contents as plain maps and lists, with JSON nulls as null. */
	public Map<String, Object> toMap() {
		HashMap<String, Object> out = new HashMap<>();
		for (Map.Entry<String, Object> e : map.entrySet()) out.put(e.getKey(), JSONText.unwrap(e.getValue()));
		return out;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	/** The JSON text, indented by {@code indentFactor} spaces per level (0: on one line). */
	public String toString(int indentFactor) {
		StringBuilder out = new StringBuilder();
		JSONText.write(out, this, indentFactor, 0);
		return out.toString();
	}

	public static String quote(String string) {
		StringBuilder out = new StringBuilder();
		JSONText.quote(out, string);
		return out.toString();
	}

	Set<Map.Entry<String, Object>> entries() {
		return map.entrySet();
	}

	private Number number(String key) {
		Number n = JSONText.toNumber(get(key));
		if (n == null) throw wrongType(key, "a number");
		return n;
	}

	private JSONException wrongType(String key, String type) {
		return new JSONException("JSONObject[" + quote(key) + "] is not " + type + ".");
	}
}
