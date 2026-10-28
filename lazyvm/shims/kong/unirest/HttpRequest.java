package kong.unirest;

/** LAZYVM: a request that never reaches the network (see {@link Unirest}). */
public final class HttpRequest {
	private final String url;

	HttpRequest(String url) {
		this.url = url;
	}

	public HttpRequest field(String name, Object value) {
		return this;
	}

	public HttpResponse<JsonNode> asJson() {
		throw offline();
	}

	/** Reports the failure to {@code callback} at once, on the calling thread. */
	public void asEmptyAsync(Callback<Empty> callback) {
		callback.failed(offline());
	}

	private UnirestException offline() {
		return new UnirestException("no network on LazyVM: " + url);
	}
}
