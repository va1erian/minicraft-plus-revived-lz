package minicraft.util;

/**
 * LAZYVM: the port's own interface for things that can be released; the game uses it to
 * unlink sprites from the SpriteLinker.
 */
public interface Destroyable {
	default void destroy() throws DestroyFailedException {
		throw new DestroyFailedException();
	}

	default boolean isDestroyed() {
		return false;
	}
}
