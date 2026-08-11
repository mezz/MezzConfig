package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.value.IConfigValue;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Consumer;

/**
 * Collects config value updates for a batch.
 * <p>
 * Use the updater passed to {@link IConfigSchema#batchUpdate(Consumer)}.
 * Values are snapshotted and validated when queued, then the complete batch is validated and applied after the callback
 * returns. Listeners are notified after all changed values have updated and persistence has been scheduled.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigBatchUpdater {
	/**
	 * Set a config value when this batch is applied.
	 * <p>
	 * If the same config value is set more than once, the last value is used.
	 * Built-in list values are copied to an unmodifiable snapshot when queued.
	 *
	 * @param configValue config value to update
	 * @param value new value
	 * @return this updater
	 *
	 * @throws IllegalArgumentException if the value is invalid
	 * @throws IllegalStateException if this updater is no longer active
	 *
	 * @since 0.1.0
	 */
	<T> IConfigBatchUpdater set(IConfigValue<T> configValue, T value);
}
