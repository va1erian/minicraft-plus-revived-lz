package org.json;

/** LAZYVM: thrown for malformed JSON text and for a missing or mistyped value (see JSONObject). */
public class JSONException extends RuntimeException {
	public JSONException(String message) {
		super(message);
	}

	public JSONException(String message, Throwable cause) {
		super(message, cause);
	}

	public JSONException(Throwable cause) {
		super(cause.getMessage(), cause);
	}
}
