package kong.unirest;

/** LAZYVM: thrown for every request (see {@link Unirest}). */
public class UnirestException extends RuntimeException {
	public UnirestException(String message) {
		super(message);
	}
}
