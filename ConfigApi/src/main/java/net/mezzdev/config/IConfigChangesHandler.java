package net.mezzdev.config;

import java.util.List;

/**
 * Applies a group of pending config changes.
 *
 * @since 19.39.0
 */
@FunctionalInterface
public interface IConfigChangesHandler {
	/**
	 * Apply the pending changes and return the most expensive update type that was required.
	 *
	 * @since 19.39.0
	 */
	ConfigValueUpdateType applyChanges(List<ConfigValueChange<?>> changes);
}
