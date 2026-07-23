package net.mezzdev.config.keybindings;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/**
 * Platform service for adapting Minecraft key mappings to config key mappings.
 */
public interface IConfigKeyMappingPlatformHelper {
	ConfigKeyBinding getValue(KeyMapping keyMapping);

	ConfigKeyBinding getDefaultValue(KeyMapping keyMapping);

	void set(KeyMapping keyMapping, ConfigKeyBinding value);

	ConfigKeyBinding normalize(ConfigKeyBinding value);

	Component getValueName(ConfigKeyBinding value);

	ConfigKeyModifier getKeyModifier(InputConstants.Key key);

	boolean hasKeyMappingConflict(
		KeyMapping candidateKeyMapping,
		ConfigKeyBinding candidateValue,
		KeyMapping existingKeyMapping
	);

	String getModNameForModId(String modId);

	boolean isInDev();
}
