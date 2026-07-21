package net.mezzdev.config.file.serializers;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.Optional;

final class ConfigValueSerializerUtil {
	private ConfigValueSerializerUtil() {

	}

	public static Optional<Component> getTranslatedValue(String configValueLocalizationKey, String valueName, String suffix) {
		String translationKey = configValueLocalizationKey + ".value." + valueName + suffix;
		if (Language.getInstance().has(translationKey)) {
			return Optional.of(Component.translatable(translationKey));
		}
		return Optional.empty();
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

}
