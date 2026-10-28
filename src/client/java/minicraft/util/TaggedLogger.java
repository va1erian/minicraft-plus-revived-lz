package minicraft.util;

import java.io.PrintStream;

/**
 * LAZYVM-GAP: a console-only stand-in for tinylog's TaggedLogger, with the overloads the game
 * calls. tinylog cannot start on LazyVM: its Configuration needs
 * java.util.concurrent.locks.ReentrantReadWriteLock, and its writing thread Thread.start().
 * This writes each entry at once, in the game's console format "[tag] LEVEL: message"; WARN and
 * ERROR go to stderr. TRACE is shown only with --debug-log-trace, as upstream configures it. The
 * log file (logs/log.txt) is not written.
 */
public final class TaggedLogger {
	private final String tag;

	TaggedLogger(String tag) {
		this.tag = tag;
	}

	public void trace(Object message) { log("TRACE", null, message, null); }
	public void trace(String message, Object... arguments) { log("TRACE", null, message, arguments); }
	public void trace(Throwable exception) { log("TRACE", exception, null, null); }
	public void trace(Throwable exception, String message) { log("TRACE", exception, message, null); }
	public void trace(Throwable exception, String message, Object... arguments) { log("TRACE", exception, message, arguments); }

	public void debug(Object message) { log("DEBUG", null, message, null); }
	public void debug(String message, Object... arguments) { log("DEBUG", null, message, arguments); }
	public void debug(Throwable exception) { log("DEBUG", exception, null, null); }
	public void debug(Throwable exception, String message) { log("DEBUG", exception, message, null); }
	public void debug(Throwable exception, String message, Object... arguments) { log("DEBUG", exception, message, arguments); }

	public void info(Object message) { log("INFO", null, message, null); }
	public void info(String message, Object... arguments) { log("INFO", null, message, arguments); }
	public void info(Throwable exception) { log("INFO", exception, null, null); }
	public void info(Throwable exception, String message) { log("INFO", exception, message, null); }
	public void info(Throwable exception, String message, Object... arguments) { log("INFO", exception, message, arguments); }

	public void warn(Object message) { log("WARN", null, message, null); }
	public void warn(String message, Object... arguments) { log("WARN", null, message, arguments); }
	public void warn(Throwable exception) { log("WARN", exception, null, null); }
	public void warn(Throwable exception, String message) { log("WARN", exception, message, null); }
	public void warn(Throwable exception, String message, Object... arguments) { log("WARN", exception, message, arguments); }

	public void error(Object message) { log("ERROR", null, message, null); }
	public void error(String message, Object... arguments) { log("ERROR", null, message, arguments); }
	public void error(Throwable exception) { log("ERROR", exception, null, null); }
	public void error(Throwable exception, String message) { log("ERROR", exception, message, null); }
	public void error(Throwable exception, String message, Object... arguments) { log("ERROR", exception, message, arguments); }

	private void log(String level, Throwable exception, Object message, Object[] arguments) {
		if (level.equals("TRACE") && !Logging.logTrace) return;
		StringBuilder line = new StringBuilder();
		line.append('[').append(tag == null ? "" : tag).append("] ").append(level).append(": ");
		if (message != null) line.append(arguments == null ? message.toString() : format(message.toString(), arguments));
		if (exception != null) line.append(message != null ? ": " : "").append(exception);
		PrintStream out = level.equals("WARN") || level.equals("ERROR") ? System.err : System.out;
		out.println(line);
		if (exception != null && (level.equals("WARN") || level.equals("ERROR"))) exception.printStackTrace(out);
	}

	/** Replaces each "{}" in turn with the next argument, as tinylog does. */
	private static String format(String message, Object[] arguments) {
		StringBuilder out = new StringBuilder();
		int next = 0, from = 0;
		for (int at; (at = message.indexOf("{}", from)) >= 0 && next < arguments.length; from = at + 2) {
			out.append(message, from, at).append(arguments[next++]);
		}
		return out.append(message.substring(from)).toString();
	}
}
