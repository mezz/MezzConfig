package net.mezzdev.config.api.value;

import net.mezzdev.config.api.schema.IConfigSchema;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.function.Consumer;

/**
 * One config value change that has already been applied.
 * <p>
 * Get changes from {@link IConfigSchema#batchUpdate(Consumer)},
 * {@link IConfigValueChangeListener#onChange(IAppliedConfigValueChange)}, or
 * {@link IConfigValueBatchChangeListener#onChange(List)}.
 * Old and new values satisfy the immutable value contract of {@link IConfigValue}; built-in lists are unmodifiable
 * snapshots and cannot be used to mutate config state.
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
