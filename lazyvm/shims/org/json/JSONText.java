package org.json;

import java.util.Collection;
import java.util.Map;

/** LAZYVM: JSON text in and out for the stand-in JSONObject and JSONArray. */
final class JSONText {
	private JSONText() {
	}

	/** Turns collections, maps and arrays into JSONArray and JSONObject; null into NULL. */
	static Object wrap(Object o) {
		if (o == null) return JSONObject.NULL;
		if (o instanceof JSONObject || o instanceof JSONArray || o instanceof String
			|| o instanceof Number || o instanceof Boolean || o instanceof Character) return o;
		if (o instanceof Collection) return new JSONArray((Collection<?>) o);
		if (o instanceof Map) return new JSONObject((Map<?, ?>) o);
		if (o.getClass().isArray()) return new JSONArray(o);
		if (o instanceof Enum) return ((Enum<?>) o).name();
		return o.toString();
	}

	static Object unwrap(Object o) {
		if (o instanceof JSONObject) return ((JSONObject) o).toMap();
		if (o instanceof JSONArray) return ((JSONArray) o).toList();
		return JSONObject.NULL.equals(o) ? null : o;
	}

	/** A number, or a string that holds one, as a Number; otherwise null. */
	static Number toNumber(Object o) {
		if (o instanceof Number) return (Number) o;
		if (o instanceof String) {
			try {
				return parseNumber(((String) o).trim());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	static Boolean toBoolean(Object o) {
		if (o instanceof Boolean) return (Boolean) o;
		if (o instanceof String) {
			if (((String) o).equalsIgnoreCase("true")) return Boolean.TRUE;
			if (((String) o).equalsIgnoreCase("false")) return Boolean.FALSE;
		}
		return null;
	}

	/** Integer or Long when the text is a whole number that fits, else Double. */
	static Number parseNumber(String s) {
		boolean whole = s.indexOf('.') < 0 && s.indexOf('e') < 0 && s.indexOf('E') < 0;
		if (whole) {
			try {
				long l = Long.parseLong(s);
				if (l == (int) l) return (int) l;
				return l;
			} catch (NumberFormatException tooLong) {
				// Falls through to a double, which loses precision but keeps the magnitude.
			}
		}
		return Double.parseDouble(s);
	}

	static void write(StringBuilder out, Object value, int indent, int depth) {
		if (value instanceof JSONObject) writeObject(out, (JSONObject) value, indent, depth);
		else if (value instanceof JSONArray) writeArray(out, ((JSONArray) value), indent, depth);
		else if (value == null || JSONObject.NULL.equals(value)) out.append("null");
		else if (value instanceof Number) out.append(numberText((Number) value));
		else if (value instanceof Boolean) out.append(value);
		else if (value instanceof String || value instanceof Character) quote(out, value.toString());
		else write(out, wrap(value), indent, depth);
	}

	private static void writeObject(StringBuilder out, JSONObject o, int indent, int depth) {
		out.append('{');
		boolean first = true;
		for (Map.Entry<String, Object> e : o.entries()) {
			if (!first) out.append(',');
			first = false;
			newline(out, indent, depth + 1);
			quote(out, e.getKey());
			out.append(indent > 0 ? ": " : ":");
			write(out, e.getValue(), indent, depth + 1);
		}
		if (!first) newline(out, indent, depth);
		out.append('}');
	}

	private static void writeArray(StringBuilder out, JSONArray a, int indent, int depth) {
		out.append('[');
		boolean first = true;
		for (Object v : a.elements()) {
			if (!first) out.append(',');
			first = false;
			newline(out, indent, depth + 1);
			write(out, v, indent, depth + 1);
		}
		if (!first) newline(out, indent, depth);
		out.append(']');
	}

	private static void newline(StringBuilder out, int indent, int depth) {
		if (indent <= 0) return;
		out.append('\n');
		for (int i = 0; i < indent * depth; i++) out.append(' ');
	}

	/** Whole doubles lose their ".0", as org.json writes them; NaN and infinities are errors. */
	private static String numberText(Number n) {
		if (n instanceof Double || n instanceof Float) {
			double d = n.doubleValue();
			if (Double.isNaN(d) || Double.isInfinite(d)) throw new JSONException("JSON does not allow non-finite numbers.");
			if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
		}
		return n.toString();
	}

	static void quote(StringBuilder out, String s) {
		if (s == null) {
			out.append("\"\"");
			return;
		}
		out.append('"');
		char prev = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"': out.append("\\\""); break;
				case '\\': out.append("\\\\"); break;
				case '/': out.append(prev == '<' ? "\\/" : "/"); break;
				case '\b': out.append("\\b"); break;
				case '\f': out.append("\\f"); break;
				case '\n': out.append("\\n"); break;
				case '\r': out.append("\\r"); break;
				case '\t': out.append("\\t"); break;
				default:
					if (c < ' ' || c >= '\u0080' && c < ' ' || c >= ' ' && c < '℀') {
						String hex = Integer.toHexString(c);
						out.append("\\u");
						for (int k = hex.length(); k < 4; k++) out.append('0');
						out.append(hex);
					} else out.append(c);
			}
			prev = c;
		}
		out.append('"');
	}

	/** A recursive-descent reader of one JSON text. */
	static final class Parser {
		private final String s;
		private int pos;

		Parser(String source) {
			if (source == null) throw new NullPointerException("source");
			s = source;
		}

		/** Requires that only white space is left. */
		void end() {
			skipSpace();
			if (pos < s.length()) throw error("Unexpected text after the value");
		}

		void readObject(JSONObject into) {
			expect('{');
			skipSpace();
			if (peek() == '}') {
				pos++;
				return;
			}
			while (true) {
				skipSpace();
				if (peek() == '}') { // A trailing comma, which org.json also accepts.
					pos++;
					return;
				}
				String key = readKey();
				skipSpace();
				expect(':');
				if (into.has(key)) throw error("Duplicate key \"" + key + "\"");
				into.put(key, readValue());
				skipSpace();
				char c = next();
				if (c == '}') return;
				if (c != ',' && c != ';') throw error("Expected a ',' or '}'");
			}
		}

		void readArray(JSONArray into) {
			expect('[');
			skipSpace();
			if (peek() == ']') {
				pos++;
				return;
			}
			while (true) {
				skipSpace();
				if (peek() == ']') { // A trailing comma.
					pos++;
					return;
				}
				into.put(readValue());
				skipSpace();
				char c = next();
				if (c == ']') return;
				if (c != ',') throw error("Expected a ',' or ']'");
			}
		}

		private Object readValue() {
			skipSpace();
			char c = peek();
			if (c == '{') {
				JSONObject o = new JSONObject();
				readObject(o);
				return o;
			}
			if (c == '[') {
				JSONArray a = new JSONArray();
				readArray(a);
				return a;
			}
			if (c == '"' || c == '\'') return readString();
			String word = readWord();
			if (word.isEmpty()) throw error("Missing value");
			if (word.equals("true")) return Boolean.TRUE;
			if (word.equals("false")) return Boolean.FALSE;
			if (word.equals("null")) return JSONObject.NULL;
			char first = word.charAt(0);
			if (first >= '0' && first <= '9' || first == '-' || first == '.') {
				try {
					return parseNumber(word);
				} catch (NumberFormatException e) {
					// org.json keeps text it cannot read as a number as a string.
				}
			}
			return word;
		}

		private String readKey() {
			char c = peek();
			if (c == '"' || c == '\'') return readString();
			String word = readWord();
			if (word.isEmpty()) throw error("A JSONObject text must have a key");
			return word;
		}

		/** An unquoted token: everything up to white space or a structural character. */
		private String readWord() {
			int start = pos;
			while (pos < s.length() && ",:]}/\\\"[{;=#".indexOf(s.charAt(pos)) < 0 && s.charAt(pos) > ' ') pos++;
			return s.substring(start, pos).trim();
		}

		private String readString() {
			char quote = next();
			StringBuilder out = new StringBuilder();
			while (true) {
				if (pos >= s.length()) throw error("Unterminated string");
				char c = s.charAt(pos++);
				if (c == quote) return out.toString();
				if (c == '\n' || c == '\r') throw error("Unterminated string");
				if (c != '\\') {
					out.append(c);
					continue;
				}
				char e = next();
				switch (e) {
					case 'b': out.append('\b'); break;
					case 'f': out.append('\f'); break;
					case 'n': out.append('\n'); break;
					case 'r': out.append('\r'); break;
					case 't': out.append('\t'); break;
					case 'u':
						if (pos + 4 > s.length()) throw error("Illegal escape");
						try {
							out.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
						} catch (NumberFormatException ex) {
							throw error("Illegal escape");
						}
						pos += 4;
						break;
					case '"': case '\'': case '\\': case '/': out.append(e); break;
					default: throw error("Illegal escape");
				}
			}
		}

		private void skipSpace() {
			while (pos < s.length() && s.charAt(pos) <= ' ') pos++;
		}

		private char peek() {
			if (pos >= s.length()) throw error("Unexpected end of text");
			return s.charAt(pos);
		}

		private char next() {
			char c = peek();
			pos++;
			return c;
		}

		private void expect(char c) {
			skipSpace();
			if (next() != c) throw error("Expected '" + c + "'");
		}

		private JSONException error(String message) {
			return new JSONException(message + " at " + pos + " [character " + (pos + 1) + "]");
		}
	}
}
