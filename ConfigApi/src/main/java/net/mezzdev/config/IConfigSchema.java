package net.mezzdev.config;

import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Config data prepared for display in a config screen.
 *
 * @since 19.39.0
 */
public interface IConfigSchema extends IConfigFile {
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
