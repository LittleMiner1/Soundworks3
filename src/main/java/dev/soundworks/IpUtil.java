package dev.soundworks;

/** Validation for IP addresses / frequencies (digits and dots only) and passwords. */
public final class IpUtil {
	private IpUtil() {}

	public static final int MAX_IP = 15;
	public static final int MAX_PASSWORD = 24;
	public static final int MAX_NAME = 24;

	/** Returns the cleaned address, "" if empty, or null if the text is not a valid address. */
	public static String normalize(String s) {
		if (s == null) return "";
		s = s.strip();
		if (s.isEmpty()) return "";
		if (s.length() > MAX_IP || s.startsWith(".") || s.endsWith(".") || s.contains("..")) return null;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (!(c >= '0' && c <= '9') && c != '.') return null;
		}
		return s;
	}

	/** Filter for text fields: only digits and dots while typing. */
	public static boolean typing(String s) {
		if (s.length() > MAX_IP) return false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (!(c >= '0' && c <= '9') && c != '.') return false;
		}
		return true;
	}

	public static String password(String s) {
		if (s == null) return "";
		s = s.strip();
		return s.length() > MAX_PASSWORD ? s.substring(0, MAX_PASSWORD) : s;
	}

	public static String name(String s, String fallback) {
		if (s == null || s.isBlank()) return fallback;
		s = s.strip();
		return s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s;
	}
}
