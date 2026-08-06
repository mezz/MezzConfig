package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.value.IConfigValue;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Consumer;

/**
 * Collects config value updates for a batch.
 * <p>
 * Use the updater passed to {@link IConfigSchema#batchUpdate(Consumer)}.
 * Values are validated and applied after the callback returns, and listeners are notified after all changed values have
 * updated.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigBatchUpdater {
	/**
	 * Set a config value when this batch is applied.
	 * <p>
	 * If the same config value is set more than once, the last value is used.
	 *
	 * @param configValue config value to update
	 * @param value new value
	 *
	 * @since 0.1.0
	 */
	<T> IConfigBatchUpdater set(IConfigValue<T> configValue, T value);
}
