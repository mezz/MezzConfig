package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.files.IConfigManager;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueBatchChangeListener;
import net.mezzdev.config.api.value.IAppliedConfigValueChange;
import net.mezzdev.config.api.value.IPendingConfigValueUpdate;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.List;

/**
 * Represents one registered config schema and its backing file.
 *
 * Config schemas contain one or more {@link IConfigCategory},
 * and each category has one or more {@link IConfigValue}.
 * <p>
 * Create and register your schema here: {@link IConfigSchemaBuilder#build()}.
 * Get registered schemas here: {@link IConfigManager#getConfigFiles()}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigSchema {
	/**
	 * Get the path of this config schema.
	 * Used to identify the backing file.
	 *
	 * Note that config values will read from this file automatically,
	 * and updating config values will save the file automatically,
	 * so you should not read or write this file yourself.
	 *
	 * @since 0.1.0
	 */
	Path getPath();

	/**
	 * Get all the categories in this schema.
	 * Each category contains values that can be read or edited.
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<? extends IConfigCategory> getCategories();

	/**
	 * Apply several config value updates together.
	 * <p>
	 * Updates are validated before any values are changed. If validation succeeds, every changed value is updated
	 * before listeners are notified.
	 * All updates must belong to this schema and each config value may only be updated once in a batch.
	 *
	 * @param updates pending updates to apply
	 * @return changes that were applied
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<? extends IAppliedConfigValueChange<?>> applyUpdates(List<? extends IPendingConfigValueUpdate<?>> updates);

	/**
	 * Add a listener that is called with every batch of changes applied to this schema.
	 *
	 * @since 0.1.0
	 */
	void addListener(IConfigValueBatchChangeListener listener);

	/**
	 * Clear listeners from this schema and its values.
	 * Useful when tearing down per-runtime listeners.
	 *
	 * @since 0.1.0
	 */
	void clearListeners();
}
