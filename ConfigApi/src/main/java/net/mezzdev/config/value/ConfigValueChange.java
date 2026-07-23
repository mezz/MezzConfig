package net.mezzdev.config.value;

/**
 * A pending update for one config value.
 *
 * @param configValue the config value to update
 * @param value the new value to apply
 *
 * @since 19.39.0
 */
public record ConfigValueChange<T>(
	IConfigValue<T> configValue,
	T value
) {

}
