package net.mezzdev.config.api.value;

import org.jetbrains.annotations.ApiStatus;

/**
 * One pending config value update.
 * <p>
 * Create an instance here: {@link IConfigValue#createUpdate(Object)}.
 *
 * @param <T> config value type
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IPendingConfigValueUpdate<T> {
	/**
	 * Get the config value to update.
	 *
	 * @since 0.1.0
	 */
	IConfigValue<T> configValue();

	/**
	 * Get the new value to apply.
	 *
	 * @since 0.1.0
	 */
	T newValue();
}
