package net.mezzdev.config.api.value.change;

import net.mezzdev.config.api.value.IConfigValue;
import org.jetbrains.annotations.ApiStatus;

/**
 * Describes one setting before and after an applied change.
 * <p>
 * Batch updates and listeners provide these objects so mods can react without re-reading every setting. Pending listeners
 * describe saved selections; effective listeners describe values currently used by the game.
 *
 * @param <T> config value type
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IAppliedConfigValueChange<T> {
	/**
	 * Get the config value that changed.
	 *
	 * @since 0.1.0
	 */
	IConfigValue<T> configValue();

	/**
	 * Get the value before the change.
	 *
	 * @since 0.1.0
	 */
	T oldValue();

	/**
	 * Get the value after the change.
	 *
	 * @since 0.1.0
	 */
	T newValue();
}
