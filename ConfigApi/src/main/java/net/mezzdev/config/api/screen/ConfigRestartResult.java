package net.mezzdev.config.api.screen;

/**
 * Result from handling config changes that require a mod restart or reload.
 *
 * @since 19.39.0
 */
public enum ConfigRestartResult {
	/**
	 * The owner mod handled the restart or reload immediately.
	 *
	 * @since 19.39.0
	 */
	HANDLED,

	/**
	 * The owner mod cannot restart or reload now, so changes will be applied on the next game start.
	 *
	 * @since 19.39.0
	 */
	NEXT_GAME_START
}
