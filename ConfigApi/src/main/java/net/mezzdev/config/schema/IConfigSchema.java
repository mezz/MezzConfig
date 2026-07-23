package net.mezzdev.config.schema;

import net.mezzdev.config.files.IConfigFile;
import net.mezzdev.config.value.ConfigValueChange;
import net.mezzdev.config.value.ConfigValueUpdateType;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Config data prepared for display in a config screen.
 *
 * @since 19.39.0
 */
public interface IConfigSchema extends IConfigFile {
	/**
	 * Load this schema from disk if needed.
	 *
	 * @since 19.39.0
	 */
	void loadIfNeeded();

	/**
	 * Mark this schema as dirty so it will be saved.
	 *
	 * @since 19.39.0
	 */
	void markDirty();

	/**
	 * Clear listeners from values in this schema.
	 *
	 * @since 19.39.0
	 */
	void clearListeners();

	/**
	 * Get the most expensive update type required by the given pending changes.
	 *
	 * @since 19.39.0
	 */
	ConfigValueUpdateType getUpdateType(List<ConfigValueChange<?>> changes);

	/**
	 * Apply the pending changes and return the most expensive update type that was required.
	 *
	 * @since 19.39.0
	 */
	ConfigValueUpdateType applyChanges(List<ConfigValueChange<?>> changes);

	/**
	 * Get the categories and ordering to display in a config screen.
	 *
	 * @since 19.39.0
	 */
	@Unmodifiable
	List<? extends IConfigDisplayCategory> getDisplayCategories();
}
