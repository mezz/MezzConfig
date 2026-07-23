package net.mezzdev.config.keybindings;

/**
 * A key or mouse binding with an optional modifier used by the config screen.
 */
public record ConfigKeyBinding(String keyName, ConfigKeyModifier modifier) {
	private static final String UNKNOWN_KEY_NAME = "key.keyboard.unknown";

	/**
	 * An unbound key binding.
	 */
	public static final ConfigKeyBinding UNKNOWN = new ConfigKeyBinding(UNKNOWN_KEY_NAME, ConfigKeyModifier.NONE);

	public ConfigKeyBinding {
		if (keyName == null) {
			keyName = UNKNOWN_KEY_NAME;
		}
		if (modifier == null) {
			modifier = ConfigKeyModifier.NONE;
		}
	}

	/**
	 * Returns true when this binding has no key or mouse button assigned.
	 */
	public boolean isUnbound() {
		return keyName.equals(UNKNOWN_KEY_NAME);
	}
}
