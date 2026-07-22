package net.mezzdev.config.keybindings;

import com.mojang.blaze3d.platform.InputConstants;
import net.mezzdev.config.ConfigKeyBinding;
import net.mezzdev.config.ConfigKeyModifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class VanillaConfigKeyMappingPlatformHelper implements IConfigKeyMappingPlatformHelper {
	@Override
	public ConfigKeyBinding getValue(KeyMapping keyMapping) {
		InputConstants.Key key = ConfigKeyBindingUtil.getKey(keyMapping);
		return ConfigKeyBindingUtil.create(key, ConfigKeyModifier.NONE);
	}

	@Override
	public ConfigKeyBinding getDefaultValue(KeyMapping keyMapping) {
		return ConfigKeyBindingUtil.create(keyMapping.getDefaultKey(), ConfigKeyModifier.NONE);
	}

	@Override
	public void set(KeyMapping keyMapping, ConfigKeyBinding value) {
		keyMapping.setKey(ConfigKeyBindingUtil.getKey(value.keyName()));
	}

	@Override
	public ConfigKeyBinding normalize(ConfigKeyBinding value) {
		return new ConfigKeyBinding(value.keyName(), ConfigKeyModifier.NONE);
	}

	@Override
	public Component getValueName(ConfigKeyBinding value) {
		return ConfigKeyBindingUtil.getDisplayName(value, this::getKeyModifier);
	}

	@Override
	public ConfigKeyModifier getKeyModifier(InputConstants.Key key) {
		return ConfigKeyBindingUtil.getKeyModifier(key);
	}

	@Override
	public boolean hasKeyMappingConflict(
		KeyMapping candidateKeyMapping,
		ConfigKeyBinding candidateValue,
		KeyMapping existingKeyMapping
	) {
		if (candidateValue.isUnbound()) {
			return false;
		}
		if (getValue(existingKeyMapping).isUnbound()) {
			return false;
		}

		ConfigKeyBinding original = getValue(candidateKeyMapping);
		try {
			set(candidateKeyMapping, candidateValue);
			return candidateKeyMapping.same(existingKeyMapping) || existingKeyMapping.same(candidateKeyMapping);
		} finally {
			set(candidateKeyMapping, original);
		}
	}

	@Override
	public String getModNameForModId(String modId) {
		return modId;
	}
}
