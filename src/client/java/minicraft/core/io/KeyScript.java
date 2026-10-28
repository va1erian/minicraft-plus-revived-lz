package minicraft.core.io;

import lazy.gfx.Key;
import lazy.gfx.Window;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * LAZYVM: debug-only scripted input for checking the port without a keyboard, enabled with
 * {@code -Dminicraft.lazyvm.keys=<file>}. The script runs one command after another, one step
 * per game tick, and feeds the InputHandler the way window events do. Commands, one per line
 * ({@code #} starts a comment):
 * <pre>
 * wait N          do nothing for N ticks
 * press KEY [N]   hold KEY (a lazy.gfx.Key name such as ENTER, W or F3) for N ticks (default 2)
 * down KEY        press KEY and keep it down
 * up KEY          release KEY
 * type TEXT       type TEXT, one character per tick
 * shot FILE       save the window's pixels as a binary PPM
 * stats           print the frames and ticks of the last second
 * quit            leave the game through its quit path
 * </pre>
 */
public final class KeyScript {
	private static final HashMap<String, Integer> codes = new HashMap<>();

	static {
		for (int code = 0; code < Key.F1 + 12; code++) {
			String name = Key.name(code);
			if (!name.equals("UNKNOWN")) codes.put(name, code);
		}
	}

	private final List<String[]> commands;
	private int next;
	private int waitTicks;
	private int heldKey = -1;
	private String typing = "";

	private KeyScript(List<String[]> commands) {
		this.commands = commands;
	}

	/** The script named by the system property {@code name}, or null if it is not set. */
	public static KeyScript fromProperty(String name) {
		String path = System.getProperty(name);
		if (path == null) return null;
		List<String[]> commands = new ArrayList<>();
		try (BufferedReader in = new BufferedReader(new FileReader(path))) {
			for (String line; (line = in.readLine()) != null; ) {
				int hash = line.indexOf('#');
				if (hash >= 0) line = line.substring(0, hash);
				line = line.trim();
				if (!line.isEmpty()) commands.add(line.split("\\s+", 2));
			}
		} catch (IOException e) {
			throw new RuntimeException("cannot read key script " + path, e);
		}
		return new KeyScript(commands);
	}

	/** Runs the script for one game tick. */
	public void tick(InputHandler input, Window window) {
		if (!typing.isEmpty()) {
			input.keyTyped(typing.charAt(0));
			typing = typing.substring(1);
			return;
		}
		if (waitTicks > 0 && --waitTicks > 0) return;
		if (heldKey >= 0) {
			input.keyReleased(heldKey);
			heldKey = -1;
		}
		if (next < commands.size()) run(commands.get(next++), input, window);
	}

	private void run(String[] cmd, InputHandler input, Window window) {
		String arg = cmd.length > 1 ? cmd[1] : "";
		switch (cmd[0]) {
			case "wait": waitTicks = Integer.parseInt(arg); break;
			case "press": {
				String[] parts = arg.split("\\s+");
				heldKey = code(parts[0]);
				input.keyPressed(heldKey);
				waitTicks = parts.length > 1 ? Integer.parseInt(parts[1]) : 2;
				break;
			}
			case "down": input.keyPressed(code(arg)); break;
			case "up": input.keyReleased(code(arg)); break;
			case "type": typing = arg; break;
			case "shot": shot(window, arg); break;
			case "stats": System.out.println("[LAZYVM] " + minicraft.core.Initializer.getCurFps() + " fps"); break;
			case "quit": minicraft.core.Game.quit(); break;
			default: throw new IllegalArgumentException("unknown key script command " + cmd[0]);
		}
	}

	private static int code(String name) {
		Integer code = codes.get(name.toUpperCase());
		if (code == null) throw new IllegalArgumentException("unknown key " + name);
		return code;
	}

	private static void shot(Window window, String path) {
		int w = window.width(), h = window.height();
		int[] rgb = window.capture();
		try (OutputStream out = new BufferedOutputStream(new FileOutputStream(path))) {
			out.write(("P6\n" + w + " " + h + "\n255\n").getBytes("US-ASCII"));
			byte[] row = new byte[w * 3];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int p = rgb[y * w + x];
					row[x * 3] = (byte) (p >> 16);
					row[x * 3 + 1] = (byte) (p >> 8);
					row[x * 3 + 2] = (byte) p;
				}
				out.write(row);
			}
		} catch (IOException e) {
			throw new RuntimeException("cannot write " + path, e);
		}
		System.out.println("[LAZYVM] saved " + path + " (" + w + "x" + h + ")");
	}
}
