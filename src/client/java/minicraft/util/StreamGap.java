package minicraft.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * LAZYVM-GAP: LazyVM has no java.util.stream yet (no Collection.stream(), Arrays.stream(),
 * Stream.of() or Collectors). These loops do what the game's stream pipelines did, so that each
 * call site changes by one line: {@code list.stream().anyMatch(p)} becomes
 * {@code StreamGap.anyMatch(list, p)}.
 */
public final class StreamGap {
	private StreamGap() {
	}

	public static <T> boolean anyMatch(Iterable<T> items, Predicate<? super T> test) {
		for (T item : items) if (test.test(item)) return true;
		return false;
	}

	public static <T> boolean anyMatch(T[] items, Predicate<? super T> test) {
		return anyMatch(Arrays.asList(items), test);
	}

	public static <T> boolean allMatch(Iterable<T> items, Predicate<? super T> test) {
		for (T item : items) if (!test.test(item)) return false;
		return true;
	}

	public static <T> int count(Iterable<T> items, Predicate<? super T> test) {
		int n = 0;
		for (T item : items) if (test.test(item)) n++;
		return n;
	}

	public static <T> int count(T[] items, Predicate<? super T> test) {
		return count(Arrays.asList(items), test);
	}

	/** The items that pass, in order (was filter + collect(Collectors.toList())). */
	public static <T> ArrayList<T> filter(Iterable<T> items, Predicate<? super T> test) {
		ArrayList<T> out = new ArrayList<>();
		for (T item : items) if (test.test(item)) out.add(item);
		return out;
	}

	/** The first item that passes, or null (was filter + findFirst/findAny + orElse(null)). */
	public static <T> T first(Iterable<T> items, Predicate<? super T> test) {
		for (T item : items) if (test.test(item)) return item;
		return null;
	}

	/** Whether every char is white space (was s.chars().allMatch(Character::isWhitespace)). */
	public static boolean allWhitespace(String s) {
		for (int i = 0; i < s.length(); i++) if (!Character.isWhitespace(s.charAt(i))) return false;
		return true;
	}

	/** Each item mapped, in order (was map + collect(Collectors.toList())). */
	public static <T, R> ArrayList<R> map(Iterable<T> items, Function<? super T, ? extends R> f) {
		ArrayList<R> out = new ArrayList<>();
		for (T item : items) out.add(f.apply(item));
		return out;
	}
}
