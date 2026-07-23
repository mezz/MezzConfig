package net.mezzdev.config.keybindings;

import net.minecraft.client.KeyMapping;

/**
 * Creates config key mapping adapters for Minecraft key mappings.
 */
public final class ConfigKeyMappings {
	private ConfigKeyMappings() {

	}

	/**
	 * Create a config key mapping from a Minecraft key mapping.
	 */
	public static IConfigKeyMapping create(KeyMapping keyMapping) {
		return new ConfigKeyMapping(keyMapping);
	}
}
