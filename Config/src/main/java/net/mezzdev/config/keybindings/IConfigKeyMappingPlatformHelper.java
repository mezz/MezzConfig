package net.mezzdev.config.keybindings;

import com.mojang.blaze3d.platform.InputConstants;
import net.mezzdev.config.ConfigKeyBinding;
import net.mezzdev.config.ConfigKeyModifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;

/**
 * Platform service for adapting Minecraft key mappings to config key mappings.
 */
@ApiStatus.Internal
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
}
