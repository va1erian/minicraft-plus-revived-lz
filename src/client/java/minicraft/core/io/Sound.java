package minicraft.core.io;

import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;

/**
 * LAZYVM: silent for now. LazyVM has no javax.sound and lazy.gfx no audio output, so sounds are
 * registered by name but never decoded or played. The upstream mixer (Sound.tick) wrote 16-bit
 * stereo PCM to a javax.sound SourceDataLine; a lazy.audio API could take its place.
 */
public class Sound {
	// Creates sounds from their respective files
	private static final HashMap<String, Sound> sounds = new HashMap<>();

	private Sound() {
	}

	public static void resetSounds() {
		sounds.clear();
	}

	public static void loadSound(String key, InputStream in, String pack) {
		sounds.put(key, new Sound());
	}

	/**
	 * Recommend {@link #play(String)} and {@link #loop(String, int)}.
	 */
	@Nullable
	public static Sound getSound(String key) {
		return sounds.get(key);
	}

	/**
	 * This method does safe check for {@link #play()}.
	 */
	public static void play(String key) {
		Sound sound = sounds.get(key);
		if (sound != null) sound.play();
	}

	/**
	 * This method does safe check for {@link #loop(int)}.
	 */
	public static void loop(String key, int count) {
		Sound sound = sounds.get(key);
		if (sound != null) sound.loop(count);
	}

	public static void tick() {
	}

	public void play() {
	}

	/** @deprecated no longer supported, but reserved for future implementation. */
	@Deprecated
	public void loop(int count) {}
}
