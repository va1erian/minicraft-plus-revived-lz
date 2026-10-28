package minicraft.level;

import lazy.gfx.Event;
import lazy.gfx.Key;
import lazy.gfx.Window;
import minicraft.core.Game;
import minicraft.level.tile.Tiles;

import java.util.Random;

public class LevelViewer {
	private static Window mapWindow;

	/**
	 * LAZYVM: shows a map in a lazy.gfx window and waits for a choice; this replaces the
	 * JOptionPane that showed the map with one button per option. Keys 1 to 9 pick an option,
	 * Enter the first one.
	 * @return the option's index, or -1 once the window is closed (or Escape is pressed).
	 */
	static int showMap(String title, int[] pixels, int w, int h, int scale, String[] options) {
		if (mapWindow == null) mapWindow = Window.open(title, w, h, scale);
		StringBuilder keys = new StringBuilder(title);
		for (int i = 0; i < options.length; i++) keys.append(i == 0 ? " | " : ", ").append(i + 1).append(": ").append(options[i]);
		mapWindow.setTitle(keys.toString());
		Event e = new Event();
		while (mapWindow.isOpen()) {
			mapWindow.present(pixels, w, h);
			while (mapWindow.poll(e)) {
				if (e.type == Event.CLOSE || e.type == Event.KEY_DOWN && e.key == Key.ESCAPE) {
					mapWindow.close();
					return -1;
				}
				if (e.type == Event.KEY_DOWN && e.key == Key.ENTER) return 0;
				if (e.type == Event.KEY_DOWN && e.key >= '1' && e.key < '1' + options.length) return e.key - '1';
			}
			try {
				Thread.sleep(16);
			} catch (InterruptedException ignored) {
			}
		}
		return -1;
	}

	public static void main(String[] args) {
		long worldSeed = 0x100;
		Random rand = new Random();

		// Fixes to get this method to work

		// AirWizard needs this in constructor
		Game.gameDir = "";

		Tiles.initTileList();
		// End of fixes

		int idx = -2;

		int[] maplvls = new int[args.length];
		boolean valid = true;
		if (maplvls.length > 0) {
			for (int i = 0; i < args.length; i++) {
				try {
					int lvlnum = Integer.parseInt(args[i]);
					maplvls[i] = lvlnum;
				} catch (Exception ex) {
					valid = false;
					break;
				}
			}
		} else valid = false;

		if (!valid) {
			maplvls = new int[1];
		}

		int lvl = maplvls[idx++ % maplvls.length];

		//noinspection InfiniteLoopStatement
		while (true) {
			int w = 320;
			int h = 320;

			//noinspection ConstantConditions
			if (lvl > 1 || lvl < -4) continue;

			ChunkManager map = LevelGen.createAndValidateMap(w, h, lvl, worldSeed);
			Level parentLevel = lvl + 1 > 1 ? null : new Level(w, h, worldSeed, lvl + 1, null, false);
			map = Level.genStructuresToMap(map, new Level(w, h, worldSeed, lvl, parentLevel, false));

			if (map == null) continue;

			int[] pixels = new int[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int i = x + y * w;

					if (map.getTile(x, y) == Tiles.get("water")) pixels[i] = 0x000080;
					if (map.getTile(x, y) == Tiles.get("iron Ore")) pixels[i] = 0x000080;
					if (map.getTile(x, y) == Tiles.get("flower")) pixels[i] = 0x000080;
					if (map.getTile(x, y) == Tiles.get("gold Ore")) pixels[i] = 0x000080;
					if (map.getTile(x, y) == Tiles.get("gem Ore")) pixels[i] = 0x000080;
					if (map.getTile(x, y) == Tiles.get("grass")) pixels[i] = 0x208020;
					if (map.getTile(x, y) == Tiles.get("rock")) pixels[i] = 0xa0a0a0;
					if (map.getTile(x, y) == Tiles.get("flower")) pixels[i] = 0xa0a0f0;
					if (map.getTile(x, y) == Tiles.get("hard rock")) pixels[i] = 0x707070;
					if (map.getTile(x, y) == Tiles.get("dirt")) pixels[i] = 0x604040;
					if (map.getTile(x, y) == Tiles.get("sand")) pixels[i] = 0xa0a040;
					if (map.getTile(x, y) == Tiles.get("Stone Bricks")) pixels[i] = 0xa0a040;
					if (map.getTile(x, y) == Tiles.get("Stone Wall")) pixels[i] = 0xa0a040;
					if (map.getTile(x, y) == Tiles.get("tree")) pixels[i] = 0x003000;
					if (map.getTile(x, y) == Tiles.get("Obsidian Wall")) pixels[i] = 0xa0a040;
					if (map.getTile(x, y) == Tiles.get("Obsidian")) pixels[i] = 0x000000;
					if (map.getTile(x, y) == Tiles.get("lava")) pixels[i] = 0xffff2020;
					if (map.getTile(x, y) == Tiles.get("cloud")) pixels[i] = 0xa0a0a0;
					if (map.getTile(x, y) == Tiles.get("Stairs Down")) pixels[i] = 0xffffffff;
					if (map.getTile(x, y) == Tiles.get("Stairs Up")) pixels[i] = 0xffffffff;
					if (map.getTile(x, y) == Tiles.get("Cloud Cactus")) pixels[i] = 0xffff00ff;
					if (map.getTile(x, y) == Tiles.get("Ornate Obsidian")) pixels[i] = 0x000f0a;
					if (map.getTile(x, y) == Tiles.get("Raw Obsidian")) pixels[i] = 0x0a0080;
					if (map.getTile(x, y) == Tiles.get((short)47)) pixels[i] = 0xa0a040; // Boss Room Tile
					if (map.getTile(x, y) == Tiles.get((short)48)) pixels[i] = 0xa0a040; // Boss Room Tile
					if (map.getTile(x, y) == Tiles.get("Wood Wall")) pixels[i] = 0xa0a040;
					if (map.getTile(x, y) == Tiles.get("Wood Planks")) pixels[i] = 0xa0a040;
					if (map.getTile(x, y) == Tiles.get("ashed dirt")) pixels[i] = 0x303030;
				}
			}
			int op = showMap("LevelViewer " + Game.VERSION + " | Seed: " + worldSeed + " | Level: " + lvl, pixels, w, h, 4,
				new String[] { "Next Seed", "Prev Seed", "Next Level", "Prev Level", "Min (0x100)", "Max (0xAAFF20)", "Random Seed", "Close" });
			if (op < 0) return; // LAZYVM: the window is gone; JOptionPane would have opened a new one.
			if (op == 0) worldSeed++;
			else if (op == 1) worldSeed--;
			else if (op == 2) {
				lvl--;
				if (lvl < -4) lvl = 1;
			}
			else if (op == 3) {
				lvl++;
				if (lvl > 1) lvl = -4;
			}
			else if (op == 4) worldSeed = 0x100;
			else if (op == 5) worldSeed = 0xAAFF20;
			else if (op == 6) worldSeed = rand.nextLong();
			else if (op == 7) return;
			else worldSeed++;
		}
	}
}
