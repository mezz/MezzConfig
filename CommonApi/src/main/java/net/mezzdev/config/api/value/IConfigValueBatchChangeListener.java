package net.mezzdev.config.api.value;

import net.mezzdev.config.api.schema.IConfigSchema;

import java.util.List;

/**
 * Listener for a batch of config value changes.
 * <p>
 * Register a schema-wide listener here: {@link IConfigSchema#addListener(IConfigValueBatchChangeListener)}.
 * Register a value-scoped listener here: {@link IConfigValue#addBatchListener(IConfigValueBatchChangeListener)}.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface IConfigValueBatchChangeListener {
	/**
	 * Called after all config values in the batch have changed.
	 *
	 * @param changes all changes applied in the batch
	 *
	 * @since 0.1.0
	 */
	void onChange(List<? extends IAppliedConfigValueChange<?>> changes);
}
