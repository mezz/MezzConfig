package net.mezzdev.config.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.mezzdev.config.keybindings.ConfigKeyBinding;
import net.mezzdev.config.keybindings.ConfigKeyModifier;
import net.mezzdev.config.keybindings.ConfigKeyBindingUtil;
import net.mezzdev.config.keybindings.VanillaConfigKeyMappingPlatformHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

public final class FabricConfigKeyMappingPlatformHelper extends VanillaConfigKeyMappingPlatformHelper {
	@Override
	public ConfigKeyBinding getValue(KeyMapping keyMapping) {
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(keyMapping);
		ConfigKeyModifier modifier = getBoundModifier(keyMapping);
		return ConfigKeyBindingUtil.create(key, modifier);
	}

	@Override
	public ConfigKeyBinding getDefaultValue(KeyMapping keyMapping) {
		ConfigKeyModifier modifier = getDefaultModifier(keyMapping);
		return ConfigKeyBindingUtil.create(keyMapping.getDefaultKey(), modifier);
	}

	@Override
	public void set(KeyMapping keyMapping, ConfigKeyBinding value) {
		keyMapping.setKey(ConfigKeyBindingUtil.getKey(value.keyName()));
		if (FabricAmecsSupport.isEnabled()) {
			AmecsConfigKeyMappingHelper.setModifier(keyMapping, value.modifier());
		}
	}

	@Override
	public ConfigKeyBinding normalize(ConfigKeyBinding value) {
		if (!FabricAmecsSupport.isEnabled()) {
			return new ConfigKeyBinding(value.keyName(), ConfigKeyModifier.NONE);
		}
		return ConfigKeyBindingUtil.normalize(value, this::getKeyModifier);
	}

	@Override
	public Component getValueName(ConfigKeyBinding value) {
		if (FabricAmecsSupport.isEnabled()) {
			return AmecsConfigKeyMappingHelper.getDisplayName(value);
		}
		return ConfigKeyBindingUtil.getDisplayName(value, this::getKeyModifier);
	}

	@Override
	public ConfigKeyModifier getKeyModifier(InputConstants.Key key) {
		if (FabricAmecsSupport.isEnabled()) {
			return AmecsConfigKeyMappingHelper.getKeyModifier(key);
		}
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
		ConfigKeyBinding existingValue = getValue(existingKeyMapping);
		if (existingValue.isUnbound()) {
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
		return FabricLoader.getInstance()
			.getModContainer(modId)
			.map(modContainer -> modContainer.getMetadata().getName())
			.orElseGet(() -> super.getModNameForModId(modId));
	}

	@Override
	public boolean isInDev() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	private static ConfigKeyModifier getBoundModifier(KeyMapping keyMapping) {
		if (!FabricAmecsSupport.isEnabled()) {
			return ConfigKeyModifier.NONE;
		}
		return AmecsConfigKeyMappingHelper.getBoundModifier(keyMapping);
	}

	private static ConfigKeyModifier getDefaultModifier(KeyMapping keyMapping) {
		if (!FabricAmecsSupport.isEnabled()) {
			return ConfigKeyModifier.NONE;
		}
		return AmecsConfigKeyMappingHelper.getDefaultModifier(keyMapping);
	}
}
