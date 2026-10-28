package minicraft.core;

import lazy.gfx.Event;
import lazy.gfx.Key;
import lazy.gfx.Window;
import minicraft.core.io.FileHandler;
import minicraft.core.io.KeyScript;
import minicraft.core.io.Localization;
import minicraft.util.Logging;
import org.jetbrains.annotations.Nullable;

public class Initializer extends Game {
	private Initializer() {
	}

	/**
	 * Reference to actual frame, also it may be null.
	 * LAZYVM: a lazy.gfx window replaces the Swing JFrame and its canvas.
	 */
	static Window frame;
	static int fra, tik; // These store the number of frames and ticks in the previous second; used for fps, at least.

	// LAZYVM: input is polled from the window each loop instead of arriving on AWT's event thread.
	private static final Event event = new Event();
	private static boolean focused = true;
	// LAZYVM: debug aids for running without a keyboard: scripted input and an fps/tps log.
	private static final KeyScript keyScript = KeyScript.fromProperty("minicraft.lazyvm.keys");
	private static final boolean logStats = "true".equals(System.getProperty("minicraft.lazyvm.stats")); // LAZYVM-GAP: no Boolean.getBoolean.

	public static Window getFrame() {
		return frame;
	}

	/** LAZYVM: replaces Canvas.hasFocus(); true unless the window reported losing the focus. */
	static boolean hasFocus() {
		return focused;
	}

	public static int getCurFps() {
		return fra;
	}

	static void parseArgs(String[] args) {
		// Parses command line arguments
		@Nullable
		String saveDir = null;
		boolean enableHardwareAcceleration = true;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("--savedir") && i + 1 < args.length) {
				i++;
				saveDir = args[i];
			} else if (args[i].equalsIgnoreCase("--fullscreen")) {
				Updater.FULLSCREEN = true;
			} else if (args[i].equalsIgnoreCase("--debug-log-time")) {
				Logging.logTime = true;
			} else if (args[i].equalsIgnoreCase("--debug-log-thread")) {
				Logging.logThread = true;
			} else if (args[i].equalsIgnoreCase("--debug-log-trace")) {
				Logging.logTrace = true;
			} else if (args[i].equalsIgnoreCase("--debug-level")) {
				Logging.logLevel = true;
			} else if (args[i].equalsIgnoreCase("--debug-filelog-full")) {
				Logging.fileLogFull = true;
			} else if (args[i].equalsIgnoreCase("--debug-locale")) {
				Localization.isDebugLocaleEnabled = true;
			} else if (args[i].equalsIgnoreCase("--debug-unloc-tracing")) {
				Localization.unlocalizedStringTracing = true;
			}
		}
		// LAZYVM-GAP: tinylog cannot run on LazyVM; minicraft.util.TaggedLogger reads the flags.

		FileHandler.determineGameDir(saveDir);
	}

	/**
	 * This is the main loop that runs the game. It:
	 * -keeps track of the amount of time that has passed
	 * -fires the ticks needed to run the game
	 * -fires the command to render out the screen.
	 */
	static void run() {
		long lastTick = System.nanoTime();
		long lastRender = System.nanoTime();
		double unprocessed = 0;
		int frames = 0;
		int ticks = 0;
		long tickNanos = 0, renderNanos = 0; // LAZYVM: measurements.
		long lastTimer1 = System.currentTimeMillis();

		while (running) {
			pollInput(); // LAZYVM: see pollInput().
			long now = System.nanoTime();
			double nsPerTick = 1E9D / Updater.normSpeed; // Nanosecs per sec divided by ticks per sec = nanosecs per tick
			if (currentDisplay == null) nsPerTick /= Updater.gamespeed;
			unprocessed += (now - lastTick) / nsPerTick; // Figures out the unprocessed time between now and lastTick.
			lastTick = now;
			// LAZYVM: the world is now built inside a tick (no threads), which takes seconds; drop
			// such a backlog instead of replaying it as a burst of game ticks.
			if (unprocessed > Updater.normSpeed) unprocessed = 1;
			while (unprocessed >= 1) { // If there is unprocessed time, then tick.
				ticks++;
				if (keyScript != null) keyScript.tick(input, frame); // LAZYVM: debug-only scripted input.
				long tickStart = System.nanoTime(); // LAZYVM: measurements.
				Updater.tick(); // Calls the tick method (in which it calls the other tick methods throughout the code.
				tickNanos += System.nanoTime() - tickStart;
				unprocessed--;
			}

			now = System.nanoTime();
			if (now >= lastRender + 1E9D / MAX_FPS / 1.01) {
				frames++;
				lastRender = now;
				Renderer.render();
				renderNanos += System.nanoTime() - now; // LAZYVM: measurements.
			}

			try {
				long curNano = System.nanoTime();
				long untilNextTick = (long) (lastTick + nsPerTick - curNano);
				long untilNextFrame = (long) (lastRender + 1E9D / MAX_FPS - curNano);
				if (untilNextTick > 1E3 && untilNextFrame > 1E3) {
					double timeToWait = Math.min(untilNextTick, untilNextFrame) / 1.2; // in nanosecond
					//noinspection BusyWait
					Thread.sleep((long) Math.floor(timeToWait / 1E6), (int) ((timeToWait - Math.floor(timeToWait)) % 1E6));
				}
			} catch (InterruptedException ignored) {
			}

			if (System.currentTimeMillis() - lastTimer1 > 1000) { //updates every 1 second
				long interval = System.currentTimeMillis() - lastTimer1;
				lastTimer1 = System.currentTimeMillis(); // Adds a second to the timer

				fra = (int) Math.round(frames * 1000D / interval); // Saves total frames in last second
				tik = (int) Math.round(ticks * 1000D / interval); // Saves total ticks in last second
				if (logStats) // LAZYVM: measurements, with the mean time of one tick and of one frame.
					System.out.println("[LAZYVM] " + fra + " fps, " + tik + " tps, " + (ticks == 0 ? 0 : tickNanos / ticks / 1000000)
						+ " ms/tick, " + (frames == 0 ? 0 : renderNanos / frames / 1000000) + " ms/frame");
				tickNanos = renderNanos = 0;
				frames = 0; // Resets frames
				ticks = 0; // Resets ticks; ie, frames and ticks only are per second
			}
		}
	}


	/**
	 * LAZYVM: drains the window's input events into the game: keys and typed text to the
	 * InputHandler (AWT used to call it from its listeners), focus changes to {@link #hasFocus()},
	 * and a close request to the game's quit path (formerly the JFrame's windowClosing).
	 */
	private static void pollInput() {
		while (frame.poll(event)) {
			switch (event.type) {
				case Event.KEY_DOWN:
					input.keyPressed(event.key);
					// LAZYVM: lazy.gfx TEXT is printable text only; AWT also typed these two, and
					// text entry relies on them. KEY_DOWN repeats while held, as AWT's typing did.
					if (event.key == Key.BACKSPACE) input.keyTyped('\b');
					if (event.key == Key.ENTER) input.keyTyped('\n');
					break;
				case Event.KEY_UP: input.keyReleased(event.key); break;
				case Event.TEXT: input.keyTyped(event.ch); break;
				case Event.FOCUS: focused = event.x == 1; break;
				case Event.CLOSE:
					Logging.GAMEHANDLER.info("Window closing");
					quit();
					break;
			}
		}
	}

	// Creates and displays the window that the game appears in.
	// LAZYVM: lazy.gfx scales and letterboxes the frame itself, so the resize listener that kept
	// Renderer.SCALE up to date is gone, and the window has no icon (lazy.gfx has no icon API).
	static void createAndDisplayFrame() {
		frame = Window.open(NAME, Renderer.WIDTH, Renderer.HEIGHT, (int) Renderer.SCALE);
	}

	/**
	 * Launching the main window.
	 * LAZYVM: nothing to do; Window.open shows the window, and it has the focus.
	 */
	static void launchWindow() {
	}

	/**
	 * Provides a String representation of the provided Throwable's stack trace
	 * that is extracted via PrintStream.
	 * @param throwable Throwable/Exception from which stack trace is to be
	 * 	extracted.
	 * @return String with provided Throwable's stack trace.
	 */
	public static String getExceptionTrace(final Throwable throwable) {
		final java.io.ByteArrayOutputStream bytestream = new java.io.ByteArrayOutputStream();
		final java.io.PrintStream printStream = new java.io.PrintStream(bytestream);
		throwable.printStackTrace(printStream);
		String exceptionStr;
		try {
			exceptionStr = bytestream.toString("UTF-8");
		} catch (Exception ex) {
			exceptionStr = "Unavailable";
		}
		return exceptionStr;
	}
}
