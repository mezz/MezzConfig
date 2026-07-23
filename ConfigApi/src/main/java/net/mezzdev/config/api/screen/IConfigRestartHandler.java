package net.mezzdev.config.api.screen;

/**
 * Handles saved config changes that cannot be applied until a mod restarts or reloads.
 *
 * @since 19.39.0
 */
@FunctionalInterface
public interface IConfigRestartHandler {
	/**
	 * Called after saving changes that require the owner mod to restart or reload.
	 *
	 * @return the result of trying to restart or reload the owner mod
	 *
	 * @since 19.39.0
	 */
	ConfigRestartResult onRestartRequired();
}
