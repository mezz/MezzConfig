package net.mezzdev.config.api.value;

import org.jetbrains.annotations.ApiStatus;

/**
 * One config value change that has already been applied.
 * <p>
 * Get changes from {@link net.mezzdev.config.api.schema.IConfigSchema#applyUpdates(java.util.List)}
 * or {@link IConfigValueBatchChangeListener#onChange(java.util.List)}.
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
