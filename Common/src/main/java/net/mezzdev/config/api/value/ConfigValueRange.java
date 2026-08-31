package net.mezzdev.config.api.value;

import java.util.Objects;

/**
 * Inclusive lower and upper bounds for a config value.
 * <p>
 * Returned by {@link IConfigValueSerializer#getRange()} when a config value should be edited as a bounded range.
 *
 * @param min smallest valid value
 * @param max largest valid value
 *
 * @since 0.1.0
 */
public record ConfigValueRange<T>(T min, T max) {
	/**
	 * Validate and create an inclusive config value range.
	 *
	 * @param min smallest valid value
	 * @param max largest valid value
	 *
	 * @since 0.1.0
	 */
	public ConfigValueRange {
		Objects.requireNonNull(min, "min");
		Objects.requireNonNull(max, "max");
	}
}
