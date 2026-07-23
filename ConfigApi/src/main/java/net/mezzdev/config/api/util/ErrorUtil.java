package net.mezzdev.config.api.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * Shared validation helpers for config API implementations.
 *
 * @since 19.39.0
 */
public final class ErrorUtil {
	private ErrorUtil() {

	}

	/**
	 * Require that a value is not null and return it for assignment.
	 *
	 * @param value the value to check
	 * @param name the parameter or field name used in the error message
	 * @param <T> the value type
	 *
	 * @since 19.39.0
	 */
	@Contract("null, _ -> fail; !null, _ -> param1")
	public static <T> T checkNotNull(@Nullable T value, String name) {
		if (value == null) {
			throw new NullPointerException(name + " must not be null.");
		}
		return value;
	}
}
