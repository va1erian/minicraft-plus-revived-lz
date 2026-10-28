package minicraft.util;

/**
 * LAZYVM-GAP: stands in for tinylog's Logger, of which the game uses only the tagged loggers
 * (see {@link TaggedLogger} for why tinylog cannot run on LazyVM).
 */
public final class Logger {
	private Logger() {
	}

	public static TaggedLogger tag(String tag) {
		return new TaggedLogger(tag);
	}

	public static TaggedLogger tags(String... tags) {
		return new TaggedLogger(tags.length == 0 ? null : String.join(", ", tags));
	}
}
