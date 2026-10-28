package kong.unirest;

/** LAZYVM: completion callback of an asynchronous request (see {@link HttpRequest}). */
public interface Callback<T> {
	void completed(HttpResponse<T> response);

	default void failed(UnirestException e) {
	}

	default void cancelled() {
	}
}
