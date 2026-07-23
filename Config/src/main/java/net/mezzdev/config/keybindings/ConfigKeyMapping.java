package net.mezzdev.config.keybindings;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

record ConfigKeyMapping(
	KeyMapping keyMapping
) implements IConfigKeyMapping {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Set<String> MISSING_LOCALIZATION_KEYS = ConcurrentHashMap.newKeySet();

	@Override
	public String getName() {
		return keyMapping.getName();
	}

	@Override
	public Component getLocalizedName() {
		return getLocalized(keyMapping.getName(), "name");
	}

	@Override
	public Component getLocalizedContext() {
		return getLocalized(keyMapping.getName() + ".context", "context");
	}

	@Override
	public Component getLocalizedDescription() {
		return getLocalized(keyMapping.getName() + ".description", "description");
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
			getLocalized(conflict, conflict.getName(), "conflict name"),
			conflictInput,
			Component.literal(getModName(platformHelper, conflict)),
			getLocalized(conflict, conflict.getCategory(), "conflict category")
		);
	}

	private Component getLocalized(String key, String description) {
		return getLocalized(keyMapping, key, description);
	}

	private static Component getLocalized(KeyMapping keyMapping, String key, String description) {
		logMissingLocalization(keyMapping, key, description);
		return Component.translatable(key);
	}

	private static void logMissingLocalization(KeyMapping keyMapping, String key, String description) {
		if (!ConfigKeyMappingPlatformServices.PLATFORM_HELPER.isInDev()) {
			return;
		}
		if (Language.getInstance().has(key)) {
			return;
		}
		if (MISSING_LOCALIZATION_KEYS.add(key)) {
			LOGGER.error("Missing {} localization for key mapping '{}': {}", description, keyMapping.getName(), key);
		}
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
