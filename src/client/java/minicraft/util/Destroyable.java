package minicraft.util;

/**
 * LAZYVM-GAP: stands in for javax.security.auth.Destroyable, which LazyVM does not provide.
 * The game uses it only to release linked sprites; the contract is the same.
 */
public interface Destroyable {
	default void destroy() throws DestroyFailedException {
		throw new DestroyFailedException();
	}

	default boolean isDestroyed() {
		return false;
	}
}
