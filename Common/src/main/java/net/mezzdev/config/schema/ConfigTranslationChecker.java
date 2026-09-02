package net.mezzdev.config.schema;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.mezzdev.config.api.schema.IConfigCategory;
import net.mezzdev.config.api.schema.IConfigEditorCategory;
import net.minecraft.locale.Language;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ConfigTranslationChecker {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Gson PRETTY_PRINTING_GSON = new GsonBuilder()
		.disableHtmlEscaping()
		.setPrettyPrinting()
		.create();

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
			.forEach(configValue -> addLocalizationKeys(localizationKeys, configValue.getEditorInfo().getLocalizationKey()));

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
			LOGGER.warn(
				"Missing config translations for '{}'. Add these entries to the appropriate language file and fill in their values:\n{}",
				path,
				createLangFileTemplate(untranslatedKeys)
			);
		}
	}

	public static String createLangFileTemplate(Collection<String> localizationKeys) {
		JsonObject translations = new JsonObject();
		localizationKeys.forEach(localizationKey -> translations.addProperty(localizationKey, ""));
		return PRETTY_PRINTING_GSON.toJson(translations);
	}

	private static void addLocalizationKeys(Set<String> localizationKeys, String localizationKey) {
		localizationKeys.add(localizationKey);
		localizationKeys.add(localizationKey + ".description");
	}
}
