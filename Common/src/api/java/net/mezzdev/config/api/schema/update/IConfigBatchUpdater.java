package net.mezzdev.config.api.schema.update;

import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.value.IConfigValue;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Consumer;

/**
 * Queues related setting changes for {@link IConfigSchema#batchUpdate(Consumer)}.
 * <p>
 * Call {@link #set(IConfigValue, Object)} for each desired value. MezzConfig validates and applies the complete group only
 * after the callback returns, so listeners never observe a partially updated batch.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigBatchUpdater {
	/**
	 * Add or replace a value change in this batch.
	 * <p>
	 * If the same config value is set more than once, the last value is used.
	 * Built-in list values are copied to an unmodifiable snapshot when queued.
	 *
	 * @param configValue config value to update
	 * @param value new value
	 * @return this updater
	 *
	 * @throws IllegalArgumentException if the value is invalid or cannot be safely serialized
	 * @throws IllegalStateException if this updater is no longer active
	 *
	 * @since 0.1.0
	 */
	<T> IConfigBatchUpdater set(IConfigValue<T> configValue, T value);
}
