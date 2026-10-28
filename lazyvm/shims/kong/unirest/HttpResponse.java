package kong.unirest;

/** LAZYVM: never constructed, since no request succeeds (see {@link Unirest}). */
public interface HttpResponse<T> {
	int getStatus();

	String getStatusText();

	T getBody();
}
