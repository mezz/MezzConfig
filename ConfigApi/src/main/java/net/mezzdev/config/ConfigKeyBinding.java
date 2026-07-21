package net.mezzdev.config;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * A key or mouse binding with an optional modifier used by the config screen.
 *
 * @since 19.39.0
 */
public record ConfigKeyBinding(InputConstants.Key key, ConfigKeyModifier modifier) {
	/**
	 * An unbound key binding.
	 *
	 * @since 19.39.0
	 */
	public static final ConfigKeyBinding UNKNOWN = new ConfigKeyBinding(InputConstants.UNKNOWN, ConfigKeyModifier.NONE);

	public ConfigKeyBinding {
		if (key == null) {
			key = InputConstants.UNKNOWN;
		}
		if (modifier == null) {
			modifier = ConfigKeyModifier.NONE;
		}
	}

	/**
	 * Returns true when this binding has no key or mouse button assigned.
	 *
	 * @since 19.39.0
	 */
	public boolean isUnbound() {
		return key.equals(InputConstants.UNKNOWN);
	}
}
