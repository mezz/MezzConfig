package net.mezzdev.config.file.serializers;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Optional;

final class ConfigValueSerializerUtil {
	private ConfigValueSerializerUtil() {

	}

	public static Optional<Component> getTranslatedValue(Component configValueName, String valueName, String suffix) {
		return getConfigValueTranslationKey(configValueName)
			.map(configValueKey -> configValueKey + ".value." + valueName + suffix)
			.filter(Language.getInstance()::has)
			.map(Component::translatable);
	}

	public static String getDisplayNameFallback(String name) {
		String[] words = name.toLowerCase().split("_");
		StringBuilder result = new StringBuilder();
		for (String word : words) {
			if (word.isEmpty()) {
				continue;
			}
			if (!result.isEmpty()) {
				result.append(' ');
			}
			result.append(Character.toUpperCase(word.charAt(0)));
			if (word.length() > 1) {
				result.append(word.substring(1));
			}
		}
		return result.toString();
	}

	private static Optional<String> getConfigValueTranslationKey(Component configValueName) {
		if (configValueName.getContents() instanceof TranslatableContents translatableContents) {
			return Optional.of(translatableContents.getKey());
		}
		return Optional.empty();
	}
}
