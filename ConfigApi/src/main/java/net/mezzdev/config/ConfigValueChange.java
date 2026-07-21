package net.mezzdev.config;

/**
 * A pending update for one config value.
 *
 * @since 19.39.0
 */
public record ConfigValueChange<T>(
	/**
	 * The config value to update.
	 *
	 * @since 19.39.0
	 */
	IConfigValue<T> configValue,
	/**
	 * The new value to apply.
	 *
	 * @since 19.39.0
	 */
	T value
) {

}
