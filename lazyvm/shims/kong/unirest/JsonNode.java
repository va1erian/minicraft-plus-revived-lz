package kong.unirest;

import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;

/** LAZYVM: never constructed, since no request succeeds (see {@link Unirest}). */
public interface JsonNode {
	JSONArray getArray();

	JSONObject getObject();
}
