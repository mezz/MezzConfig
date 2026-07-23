package net.mezzdev.config.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import net.mezzdev.config.keybindings.ConfigKeyBinding;
import net.mezzdev.config.keybindings.ConfigKeyModifier;
import net.mezzdev.config.keybindings.ConfigKeyBindingUtil;
import net.mezzdev.config.keybindings.VanillaConfigKeyMappingPlatformHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

public final class NeoForgeConfigKeyMappingPlatformHelper extends VanillaConfigKeyMappingPlatformHelper {
	@Override
	public ConfigKeyBinding getValue(KeyMapping keyMapping) {
		return ConfigKeyBindingUtil.create(keyMapping.getKey(), fromNeoForge(keyMapping.getKeyModifier()));
	}

	@Override
	public ConfigKeyBinding getDefaultValue(KeyMapping keyMapping) {
		return ConfigKeyBindingUtil.create(keyMapping.getDefaultKey(), fromNeoForge(keyMapping.getDefaultKeyModifier()));
	}

	@Override
	public void set(KeyMapping keyMapping, ConfigKeyBinding value) {
		InputConstants.Key key = ConfigKeyBindingUtil.getKey(value.keyName());
		keyMapping.setKeyModifierAndCode(toNeoForge(value.modifier()), key);
		keyMapping.setKey(key);
	}

	@Override
	public ConfigKeyBinding normalize(ConfigKeyBinding value) {
		return ConfigKeyBindingUtil.normalize(value, this::getKeyModifier);
	}

	@Override
	public Component getValueName(ConfigKeyBinding value) {
		KeyModifier modifier = toNeoForge(value.modifier());
		InputConstants.Key key = ConfigKeyBindingUtil.getKey(value.keyName());
		return modifier.getCombinedName(key, () -> ConfigKeyBindingUtil.getKeyDisplayName(key));
	}

	@Override
	public ConfigKeyModifier getKeyModifier(InputConstants.Key key) {
		return fromNeoForge(KeyModifier.getKeyModifier(key));
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

		if (!hasKeyContextConflict(candidateKeyMapping, existingKeyMapping)) {
			return false;
		}

		ConfigKeyBinding original = getValue(candidateKeyMapping);
		try {
			set(candidateKeyMapping, candidateValue);
			return candidateKeyMapping.same(existingKeyMapping);
		} finally {
			set(candidateKeyMapping, original);
		}
	}

	@Override
	public String getModNameForModId(String modId) {
		return ModList.get()
			.getModContainerById(modId)
			.map(modContainer -> modContainer.getModInfo().getDisplayName())
			.orElse(super.getModNameForModId(modId));
	}

	@Override
	public boolean isInDev() {
		return !FMLLoader.isProduction();
	}

	private boolean hasKeyContextConflict(KeyMapping keyMapping, KeyMapping otherKeyMapping) {
		IKeyConflictContext neoForgeContext = keyMapping.getKeyConflictContext();
		IKeyConflictContext otherNeoForgeContext = otherKeyMapping.getKeyConflictContext();
		return neoForgeContext.conflicts(otherNeoForgeContext) || otherNeoForgeContext.conflicts(neoForgeContext);
	}

	private static KeyModifier toNeoForge(ConfigKeyModifier modifier) {
		return switch (modifier) {
			case CONTROL_OR_COMMAND -> KeyModifier.CONTROL;
			case SHIFT -> KeyModifier.SHIFT;
			case ALT -> KeyModifier.ALT;
			case NONE -> KeyModifier.NONE;
		};
	}

	private static ConfigKeyModifier fromNeoForge(KeyModifier modifier) {
		return switch (modifier) {
			case CONTROL -> ConfigKeyModifier.CONTROL_OR_COMMAND;
			case SHIFT -> ConfigKeyModifier.SHIFT;
			case ALT -> ConfigKeyModifier.ALT;
			case NONE -> ConfigKeyModifier.NONE;
		};
	}
}
