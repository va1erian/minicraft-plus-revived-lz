package minicraft.util;

/** LAZYVM: the port's own exception, for a {@link Destroyable} that cannot be released. */
public class DestroyFailedException extends Exception {
	public DestroyFailedException() {
	}

	public DestroyFailedException(String message) {
		super(message);
	}
}
