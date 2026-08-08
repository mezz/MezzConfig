package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.IConfigCategory;
import net.mezzdev.config.api.schema.IConfigEditorCategory;
import net.minecraft.locale.Language;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public final class ConfigTranslationChecker {
	private static final Logger LOGGER = LogManager.getLogger();

	private ConfigTranslationChecker() {

	}

	public static List<String> getUntranslatedKeys(
		Collection<? extends IConfigEditorCategory> editorCategories,
		Collection<? extends IConfigCategory> categories
	) {
		Set<String> localizationKeys = new LinkedHashSet<>();
		editorCategories.forEach(category -> addLocalizationKeys(localizationKeys, category.getLocalizationKey()));
		categories.stream()
			.flatMap(category -> category.getConfigValues().stream())
			.forEach(configValue -> addLocalizationKeys(localizationKeys, configValue.getLocalizationKey()));

		Language language = Language.getInstance();
		return localizationKeys.stream()
			.filter(localizationKey -> !language.has(localizationKey))
			.toList();
	}

	public static void logUntranslatedKeys(
		Path path,
		Collection<? extends IConfigEditorCategory> editorCategories,
		Collection<? extends IConfigCategory> categories
	) {
		List<String> untranslatedKeys = getUntranslatedKeys(editorCategories, categories);
		if (!untranslatedKeys.isEmpty()) {
			LOGGER.warn("Untranslated config localization keys for '{}': {}", path, String.join(", ", untranslatedKeys));
		}
	}

	private static void addLocalizationKeys(Set<String> localizationKeys, String localizationKey) {
		localizationKeys.add(localizationKey);
		localizationKeys.add(localizationKey + ".description");
	}
}
