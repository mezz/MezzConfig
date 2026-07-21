package net.mezzdev.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A modifier key that can be combined with a key binding.
 *
 * @since 19.39.0
 */
public enum ConfigKeyModifier {
	CONTROL_OR_COMMAND {
		@Override
		public Component getCombinedName(Component component) {
			if (Minecraft.ON_OSX) {
				return Component.translatable("jei.key.combo.command", component);
			}
			return Component.translatable("jei.key.combo.control", component);
		}

		@Override
		public Component getDisplayName() {
			if (Minecraft.ON_OSX) {
				return Component.translatable("jei.key.modifier.command");
			}
			return Component.translatable("jei.key.modifier.control");
		}
	},
	SHIFT {
		@Override
		public Component getCombinedName(Component component) {
			return Component.translatable("jei.key.combo.shift", component);
		}

		@Override
		public Component getDisplayName() {
			return Component.translatable("jei.key.modifier.shift");
		}
	},
	ALT {
		@Override
		public Component getCombinedName(Component component) {
			return Component.translatable("jei.key.combo.alt", component);
		}

		@Override
		public Component getDisplayName() {
			return Component.translatable("jei.key.modifier.alt");
		}
	},
	NONE {
		@Override
		public Component getCombinedName(Component component) {
			return component;
		}

		@Override
		public Component getDisplayName() {
			return Component.empty();
		}
	};

	/**
	 * Get the display name for a key binding combined with this modifier.
	 *
	 * @since 19.39.0
	 */
	public abstract Component getCombinedName(Component component);

	/**
	 * Get the display name for this modifier.
	 *
	 * @since 19.39.0
	 */
	public abstract Component getDisplayName();

	/**
	 * Get the currently held key modifier.
	 *
	 * @since 19.39.0
	 */
	public static ConfigKeyModifier getActive() {
		if (Screen.hasShiftDown()) {
			return ConfigKeyModifier.SHIFT;
		}
		if (Screen.hasControlDown()) {
			return ConfigKeyModifier.CONTROL_OR_COMMAND;
		}
		if (Screen.hasAltDown()) {
			return ConfigKeyModifier.ALT;
		}
		return ConfigKeyModifier.NONE;
	}
}
