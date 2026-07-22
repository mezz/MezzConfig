package net.mezzdev.config.keybindings;

import com.mojang.blaze3d.platform.InputConstants;
import net.mezzdev.config.ConfigKeyBinding;
import net.mezzdev.config.ConfigKeyMappingConflict;
import net.mezzdev.config.ConfigKeyModifier;
import net.mezzdev.config.IConfigKeyMapping;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

record ConfigKeyMapping(
	KeyMapping keyMapping,
	Component localizedContext,
	Component localizedDescription
) implements IConfigKeyMapping {
	@Override
	public String getName() {
		return keyMapping.getName();
	}

	@Override
	public Component getLocalizedName() {
		return Component.translatable(keyMapping.getName());
	}

	@Override
	public Component getLocalizedContext() {
		return localizedContext;
	}

	@Override
	public Component getLocalizedDescription() {
		return localizedDescription;
	}

	@Override
	public ConfigKeyBinding getValue() {
		return ConfigKeyMappingPlatformServices.PLATFORM_HELPER.getValue(keyMapping);
	}

	@Override
	public ConfigKeyBinding getDefaultValue() {
		return ConfigKeyMappingPlatformServices.PLATFORM_HELPER.getDefaultValue(keyMapping);
	}

	@Override
	public ConfigKeyBinding normalize(ConfigKeyBinding value) {
		return ConfigKeyMappingPlatformServices.PLATFORM_HELPER.normalize(value);
	}

	@Override
	public void set(ConfigKeyBinding value) {
		ConfigKeyMappingPlatformServices.PLATFORM_HELPER.set(keyMapping, value);
	}

	@Override
	public Component getValueName(ConfigKeyBinding value) {
		return ConfigKeyMappingPlatformServices.PLATFORM_HELPER.getValueName(value);
	}

	@Override
	public ConfigKeyModifier getKeyModifier(String keyName) {
		InputConstants.Key key = ConfigKeyBindingUtil.getKey(keyName);
		return ConfigKeyMappingPlatformServices.PLATFORM_HELPER.getKeyModifier(key);
	}

	@Override
	@Unmodifiable
	public List<ConfigKeyMappingConflict> getConflicts(ConfigKeyBinding value) {
		IConfigKeyMappingPlatformHelper platformHelper = ConfigKeyMappingPlatformServices.PLATFORM_HELPER;
		ConfigKeyBinding normalizedValue = platformHelper.normalize(value);
		if (normalizedValue.isUnbound()) {
			return List.of();
		}
		KeyMapping[] keyMappings = Minecraft.getInstance().options.keyMappings;
		List<ConfigKeyMappingConflict> conflicts = new ArrayList<>();
		for (KeyMapping otherKey : keyMappings) {
			if (otherKey != keyMapping && platformHelper.hasKeyMappingConflict(keyMapping, normalizedValue, otherKey)) {
				conflicts.add(createConflict(platformHelper, otherKey));
			}
		}
		return List.copyOf(conflicts);
	}

	private static ConfigKeyMappingConflict createConflict(IConfigKeyMappingPlatformHelper platformHelper, KeyMapping conflict) {
		ConfigKeyBinding conflictValue = platformHelper.getValue(conflict);
		Component conflictInput = platformHelper.getValueName(conflictValue);
		return new ConfigKeyMappingConflict(
			Component.translatable(conflict.getName()),
			conflictInput,
			Component.literal(getModName(platformHelper, conflict)),
			Component.translatable(conflict.getCategory())
		);
	}

	private static String getModName(IConfigKeyMappingPlatformHelper platformHelper, KeyMapping keyMapping) {
		String modId = getModId(keyMapping);
		return platformHelper.getModNameForModId(modId);
	}

	private static String getModId(KeyMapping keyMapping) {
		String keyName = keyMapping.getName();
		if (keyName.startsWith("key.")) {
			String[] parts = keyName.split("\\.");
			if (parts.length > 2 && !parts[1].equals("category") && !parts[1].equals("categories")) {
				return parts[1].toLowerCase(Locale.ROOT);
			}
		}
		return getModIdFromCategory(keyMapping.getCategory());
	}

	private static String getModIdFromCategory(String category) {
		if (category.startsWith("key.categories.")) {
			return "minecraft";
		}
		if (category.startsWith("key.category.")) {
			String[] parts = category.split("\\.");
			if (parts.length > 2) {
				return parts[2].toLowerCase(Locale.ROOT);
			}
		}
		return "minecraft";
	}
}
