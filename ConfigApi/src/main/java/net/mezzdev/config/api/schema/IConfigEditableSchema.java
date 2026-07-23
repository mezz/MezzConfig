package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.value.ConfigValueChange;
import net.mezzdev.config.api.value.ConfigValueUpdateType;

import java.util.List;

/**
 * Config schema that can apply pending changes.
 *
 * @since 19.39.0
 */
public interface IConfigEditableSchema extends IConfigSchema {
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
}
