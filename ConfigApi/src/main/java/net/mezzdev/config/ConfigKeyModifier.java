package net.mezzdev.config;

/**
 * A modifier key that can be combined with a key binding.
 *
 * @since 19.39.0
 */
public enum ConfigKeyModifier {
	/**
	 * Control on most platforms, or Command on macOS.
	 *
	 * @since 19.39.0
	 */
	CONTROL_OR_COMMAND,

	/**
	 * Shift.
	 *
	 * @since 19.39.0
	 */
	SHIFT,

	/**
	 * Alt.
	 *
	 * @since 19.39.0
	 */
	ALT,

	/**
	 * No key modifier.
	 *
	 * @since 19.39.0
	 */
	NONE
}
