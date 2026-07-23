package net.mezzdev.config.forge;

import com.mojang.blaze3d.platform.InputConstants;
import net.mezzdev.config.keybindings.ConfigKeyBinding;
import net.mezzdev.config.keybindings.ConfigKeyModifier;
import net.mezzdev.config.keybindings.ConfigKeyBindingUtil;
import net.mezzdev.config.keybindings.VanillaConfigKeyMappingPlatformHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

public final class ForgeConfigKeyMappingPlatformHelper extends VanillaConfigKeyMappingPlatformHelper {
	@Override
	public ConfigKeyBinding getValue(KeyMapping keyMapping) {
		return ConfigKeyBindingUtil.create(keyMapping.getKey(), fromForge(keyMapping.getKeyModifier()));
	}

	@Override
	public ConfigKeyBinding getDefaultValue(KeyMapping keyMapping) {
		return ConfigKeyBindingUtil.create(keyMapping.getDefaultKey(), fromForge(keyMapping.getDefaultKeyModifier()));
	}

	@Override
	public void set(KeyMapping keyMapping, ConfigKeyBinding value) {
		InputConstants.Key key = ConfigKeyBindingUtil.getKey(value.keyName());
		keyMapping.setKeyModifierAndCode(toForge(value.modifier()), key);
		keyMapping.setKey(key);
	}

	@Override
	public ConfigKeyBinding normalize(ConfigKeyBinding value) {
		return ConfigKeyBindingUtil.normalize(value, this::getKeyModifier);
	}

	@Override
	public Component getValueName(ConfigKeyBinding value) {
		KeyModifier modifier = toForge(value.modifier());
		InputConstants.Key key = ConfigKeyBindingUtil.getKey(value.keyName());
		return modifier.getCombinedName(key, () -> ConfigKeyBindingUtil.getKeyDisplayName(key));
	}

	@Override
	public ConfigKeyModifier getKeyModifier(InputConstants.Key key) {
		KeyModifier modifier = KeyModifier.getModifier(key);
		if (modifier == null) {
			return ConfigKeyModifier.NONE;
		}
		return fromForge(modifier);
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
		IKeyConflictContext forgeContext = keyMapping.getKeyConflictContext();
		IKeyConflictContext otherForgeContext = otherKeyMapping.getKeyConflictContext();
		return forgeContext.conflicts(otherForgeContext) || otherForgeContext.conflicts(forgeContext);
	}

	private static KeyModifier toForge(ConfigKeyModifier modifier) {
		return switch (modifier) {
			case CONTROL_OR_COMMAND -> KeyModifier.CONTROL;
			case SHIFT -> KeyModifier.SHIFT;
			case ALT -> KeyModifier.ALT;
			case NONE -> KeyModifier.NONE;
		};
	}

	private static ConfigKeyModifier fromForge(KeyModifier modifier) {
		return switch (modifier) {
			case CONTROL -> ConfigKeyModifier.CONTROL_OR_COMMAND;
			case SHIFT -> ConfigKeyModifier.SHIFT;
			case ALT -> ConfigKeyModifier.ALT;
			case NONE -> ConfigKeyModifier.NONE;
		};
	}
}
