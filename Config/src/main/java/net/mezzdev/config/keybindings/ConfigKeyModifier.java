package net.mezzdev.config.keybindings;

/**
 * A modifier key that can be combined with a key binding.
 */
public enum ConfigKeyModifier {
	/**
	 * Control on most platforms, or Command on macOS.
	 */
	CONTROL_OR_COMMAND,

	/**
	 * Shift.
	 */
	SHIFT,

	/**
	 * Alt.
	 */
	ALT,

	/**
	 * No key modifier.
	 */
	NONE
}
