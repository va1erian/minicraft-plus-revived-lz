package minicraft.util;

/** LAZYVM-GAP: stands in for javax.security.auth.DestroyFailedException (see {@link Destroyable}). */
public class DestroyFailedException extends Exception {
	public DestroyFailedException() {
	}

	public DestroyFailedException(String message) {
		super(message);
	}
}
