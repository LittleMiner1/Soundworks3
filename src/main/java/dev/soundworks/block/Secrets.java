package dev.soundworks.block;

import java.util.function.Supplier;

/**
 * Passwords are saved to disk but must never be sent to clients. Block entities call their network-sync code
 * through {@link #hide}, and skip secret fields in saveAdditional while {@link #hidden()} is true.
 */
public final class Secrets {
	private Secrets() {}

	private static final ThreadLocal<Boolean> HIDE = ThreadLocal.withInitial(() -> false);

	public static boolean hidden() {
		return HIDE.get();
	}

	public static <T> T hide(Supplier<T> action) {
		boolean old = HIDE.get();
		HIDE.set(true);
		try {
			return action.get();
		} finally {
			HIDE.set(old);
		}
	}
}
