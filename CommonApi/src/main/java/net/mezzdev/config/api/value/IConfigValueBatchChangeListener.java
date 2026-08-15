package net.mezzdev.config.api.value;

import net.mezzdev.config.api.schema.IConfigSchema;

import java.util.List;

/**
 * Listener for a batch of effective config value changes.
 * <p>
 * Register a schema-wide listener here: {@link IConfigSchema#addListener(IConfigValueBatchChangeListener)}.
 * Register a value-scoped listener here: {@link IConfigValue#addBatchListener(IConfigValueBatchChangeListener)}.
 * The registration methods return a callback that unsubscribes the listener.
 * <p>
 * Callbacks run synchronously on the thread applying the batch; MezzConfig does not dispatch them to another thread.
 * API updates use the thread calling {@link IConfigValue#set(Object)} or
 * {@link IConfigSchema#batchUpdate(java.util.function.Consumer)}, while file-backed updates use the thread that next
 * loads the changed schema. A runtime exception from one callback is logged and does not prevent persistence or later
 * callbacks.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface IConfigValueBatchChangeListener {
	/**
	 * Called after all effective values in the batch have changed.
	 *
	 * @param changes all changes applied in the batch
	 *
	 * @since 0.1.0
	 */
	void onChange(List<? extends IAppliedConfigValueChange<?>> changes);
}
