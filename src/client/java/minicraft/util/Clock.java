package minicraft.util;

/**
 * LAZYVM-GAP: LazyVM has no java.time (nor java.util.Calendar). These give the calendar date
 * and an ISO local date-time text from System.currentTimeMillis(), in UTC since LazyVM reports
 * no time zone. The date comes from the day count since 1970 by the proleptic Gregorian rules
 * (H. Hinnant's days-to-civil method).
 */
public final class Clock {
	private Clock() {
	}

	/** {year, month (1 to 12), day of month} today, in UTC. */
	public static int[] today() {
		return civil(System.currentTimeMillis() / 86_400_000L);
	}

	/** Now as "yyyy-MM-ddTHH:mm:ss.SSS" in UTC, the form LocalDateTime.toString and parse use. */
	public static String isoNow() {
		long millis = System.currentTimeMillis();
		int[] date = civil(millis / 86_400_000L);
		long ms = millis % 86_400_000L;
		return String.format("%04d-%02d-%02dT%02d:%02d:%02d.%03d", date[0], date[1], date[2],
			ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000);
	}

	private static int[] civil(long days) {
		long z = days + 719_468; // Days since 0000-03-01.
		long era = z / 146_097; // 400-year eras; z is never negative here.
		long doe = z - era * 146_097; // Day of era, 0 to 146096.
		long yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365; // Year of era.
		long doy = doe - (365 * yoe + yoe / 4 - yoe / 100); // Day of the year starting in March.
		long mp = (5 * doy + 2) / 153; // Month starting in March, 0 to 11.
		int day = (int) (doy - (153 * mp + 2) / 5 + 1);
		int month = (int) (mp < 10 ? mp + 3 : mp - 9);
		int year = (int) (yoe + era * 400 + (month <= 2 ? 1 : 0));
		return new int[] { year, month, day };
	}
}
