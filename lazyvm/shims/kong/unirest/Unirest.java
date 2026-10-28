package kong.unirest;

/**
 * LAZYVM: stand-in for the Unirest HTTP client. LazyVM has no network stack the game can use,
 * so every request fails the way an unreachable server would.
 */
public final class Unirest {
	private Unirest() {
	}

	public static HttpRequest get(String url) {
		return new HttpRequest(url);
	}

	public static HttpRequest post(String url) {
		return new HttpRequest(url);
	}
}
