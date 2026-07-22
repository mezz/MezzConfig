package net.mezzdev.config.keybindings;

import net.mezzdev.config.IConfigKeyMapping;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/**
 * Creates config key mapping adapters for Minecraft key mappings.
 *
 * @since 19.39.0
 */
public final class ConfigKeyMappings {
	private ConfigKeyMappings() {

	}

	/**
	 * Create a config key mapping from a Minecraft key mapping.
	 *
	 * @since 19.39.0
	 */
	public static IConfigKeyMapping create(KeyMapping keyMapping, Component localizedContext) {
		return create(
			keyMapping,
			localizedContext,
			Component.translatable(keyMapping.getName() + ".description")
		);
	}

	/**
	 * Create a config key mapping from a Minecraft key mapping.
	 *
	 * @since 19.39.0
	 */
	public static IConfigKeyMapping create(
		KeyMapping keyMapping,
		Component localizedContext,
		Component localizedDescription
	) {
		return new ConfigKeyMapping(
			keyMapping,
			localizedContext,
			localizedDescription
		);
	}
}
