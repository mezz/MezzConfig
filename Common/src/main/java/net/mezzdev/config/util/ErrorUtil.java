package net.mezzdev.config.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public final class ErrorUtil {
	private ErrorUtil() {

	}

	@Contract("null, _ -> fail; !null, _ -> param1")
	public static <T> T checkNotNull(@Nullable T value, String name) {
		if (value == null) {
			throw new NullPointerException(name + " must not be null.");
		}
		return value;
	}

	@Contract("null, _ -> fail; !null, _ -> param1")
	public static String checkNotBlank(@Nullable String value, String name) {
		value = checkNotNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank.");
		}
		return value;
	}
}
