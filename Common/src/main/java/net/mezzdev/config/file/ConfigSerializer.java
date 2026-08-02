package net.mezzdev.config.file;

import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.schema.ConfigCategory;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.value.AppliedConfigValueChange;
import net.mezzdev.config.value.ConfigValueMigration;
import net.mezzdev.config.value.ConfigValueReference;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigSerializer {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String CONFIG_NAME_KEY = "mezz_config.config.name";
	private static final String CONFIG_DESCRIPTION_KEY = "mezz_config.config.description";
	private static final String CONFIG_VALUE_VALUES_KEY = "mezz_config.config.valueValues";
	private static final String CONFIG_DEFAULT_VALUE_KEY = "mezz_config.config.defaultValue";
	private static final Pattern commentRegex = Pattern.compile("\\s*#.*");
	private static final Pattern categoryRegex = Pattern.compile("\\[(?<category>\\w+)]\\s*");
	private static final Pattern keyValueRegex = Pattern.compile("\\s*(?<key>\\w+)\\s*=\\s*(?<value>.*)");
	private static final Map<Path, FileTime> saveTimes = new HashMap<>();

	private static String getLineErrorString(Path path, int lineNumber, String line, String errorMessage) {
		return """
			%s
			Config file: %s
			Line #%s: "%s\"""".formatted(errorMessage, path, lineNumber, line);
	}

	public static List<AppliedConfigValueChange<?>> load(
		Path path,
		List<ConfigCategory> categories
	) throws IOException {
		FileTime lastModifiedTime = Files.getLastModifiedTime(path);
		FileTime savedTime = saveTimes.get(path);
		if (savedTime != null && savedTime.compareTo(lastModifiedTime) >= 0) {
			LOGGER.debug("Skipping loading config file, it was just saved by us: {}", path);
			return List.of();
		}

		LOGGER.debug("Loading config file: {}", path);
		List<String> lines = Files.readAllLines(path);

		Map<String, ConfigCategory> categoriesMap = new LinkedHashMap<>();
		for (ConfigCategory category : categories) {
			categoriesMap.put(category.getName(), category);
		}

		List<AppliedConfigValueChange<?>> changes = new ArrayList<>();
		String categoryName = "";
		ConfigCategory category = null;
		for (int i = 0; i < lines.size(); i++) {
			int lineNumber = i + 1;
			String line = lines.get(i);
			if (line.isBlank() || commentRegex.matcher(line).matches()) {
				continue;
			}
			Matcher categoryMatcher = categoryRegex.matcher(line);
			if (categoryMatcher.matches()) {
				categoryName = categoryMatcher.group("category");
				category = categoriesMap.get(categoryName);
				if (category == null && !hasMovedValues(categoryName, categories)) {
					LOGGER.error(getLineErrorString(path, lineNumber, line,
						"""
						'[%s]' is not a valid category name.
						Valid names are: [%s]
						Skipping all values until the first valid category is declared."""
							.formatted(
								categoryName,
								String.join(", ", categoriesMap.keySet())
							)
					));
				}
				continue;
			}
			if (categoryName.isEmpty()) {
				LOGGER.error(getLineErrorString(path, lineNumber, line, """
				Expected a '[category]' here.
				Configs must start with a category before defining values.
				Skipping all lines until the first valid category is declared."""));
				continue;
			}

			Matcher keyValueMatcher = keyValueRegex.matcher(line);
			if (keyValueMatcher.matches()) {
				final String key = keyValueMatcher.group("key").trim();
				final String value = keyValueMatcher.group("value").trim();
				Optional<ConfigValue<?>> configValue = getConfigValue(category, key);
				if (configValue.isEmpty()) {
					ConfigValueReference legacyValueReference = new ConfigValueReference(categoryName, key);
					List<ConfigValueMigration<?>> migrations = getMovedValueMigrations(categories, legacyValueReference);
					if (migrations.isEmpty()) {
						logUnknownConfigValue(path, lineNumber, line, category, categoryName, key);
					} else {
						List<String> errors = new ArrayList<>();
						migrations.forEach(migration -> errors.addAll(migration.migrate(value, changes)));
						if (!errors.isEmpty()) {
							logDeserializeErrors(path, lineNumber, line, value, errors);
						}
					}
				} else {
					List<String> errors = configValue.get()
						.setFromSerializedValue(value, changes);
					if (!errors.isEmpty()) {
						ConfigValueReference legacyValueReference = new ConfigValueReference(categoryName, key);
						List<ConfigValueMigration<?>> migrations = getMovedValueMigrations(categories, legacyValueReference);
						if (migrations.isEmpty()) {
							logDeserializeErrors(path, lineNumber, line, value, errors);
						} else {
							List<String> migrationErrors = new ArrayList<>();
							migrations.forEach(migration -> migrationErrors.addAll(migration.migrate(value, changes)));
							if (!migrationErrors.isEmpty()) {
								logDeserializeErrors(path, lineNumber, line, value, migrationErrors);
							}
						}
					}
				}
			} else {
				LOGGER.error(getLineErrorString(path, lineNumber, line,
					"""
						Encountered an invalid line.
						Every line in the config must be either:
						 * a '[category]'
						 * a 'key = value' pair
						 * a '#'-prefixed comment"""
				));
			}
		}
		return ConfigValue.notifyChangedValues(changes);
	}

	private static Optional<ConfigValue<?>> getConfigValue(@Nullable ConfigCategory category, String key) {
		if (category == null) {
			return Optional.empty();
		}
		return category.getConfigValue(key);
	}

	private static List<ConfigValueMigration<?>> getMovedValueMigrations(List<ConfigCategory> categories, ConfigValueReference reference) {
		return categories.stream()
			.flatMap(category -> category.getMovedValueMigrations(reference).stream())
			.toList();
	}

	private static boolean hasMovedValues(String categoryName, List<ConfigCategory> categories) {
		return categories.stream()
			.anyMatch(category -> category.hasMovedValuesFromCategory(categoryName));
	}

	private static void logUnknownConfigValue(
		Path path,
		int lineNumber,
		String line,
		@Nullable ConfigCategory category,
		String categoryName,
		String key
	) {
		if (category == null) {
			LOGGER.error(getLineErrorString(path, lineNumber, line,
				"""
				'%s' is not a valid config category.
				Skipping this key."""
					.formatted(categoryName)
			));
			return;
		}
		LOGGER.error(getLineErrorString(path, lineNumber, line,
			"""
			'%s' is not a valid config key for config category '%s'.
			Valid keys: [%s]
			Skipping this key."""
				.formatted(
					key, category.getName(),
					String.join(", ", category.getValueNames())
				)
		));
	}

	private static void logDeserializeErrors(
		Path path,
		int lineNumber,
		String line,
		String value,
		List<String> errors
	) {
		String errorMessage = """
			Encountered Errors when deserializing value '%s':
			%s""".formatted(value, String.join("\n", errors));
		LOGGER.error(getLineErrorString(path, lineNumber, line, errorMessage));
	}

	public static void save(Path path, List<ConfigCategory> categories) throws IOException {
		List<String> serialized = new ArrayList<>();
		categories.forEach(category -> {
			serializeCategory(serialized, category);
			serialized.add("");
		});
		LOGGER.debug("Saving config file: {}", path);
		ConfigFileUtil.writeUsingTempFile(path, serialized);
		FileTime lastModifiedTime = Files.getLastModifiedTime(path);
		saveTimes.put(path, lastModifiedTime);
	}

	public static boolean canLocalizeComments() {
		Language language = Language.getInstance();
		return language.has(CONFIG_NAME_KEY) &&
			language.has(CONFIG_DESCRIPTION_KEY) &&
			language.has(CONFIG_VALUE_VALUES_KEY) &&
			language.has(CONFIG_DEFAULT_VALUE_KEY);
	}

	private static void serializeCategory(List<String> serialized, ConfigCategory category) {
		serialized.add("[%s]".formatted(category.getName()));
		for (ConfigValue<?> value : category.getConfigValues()) {
			serializeConfigValue(serialized, value);
			serialized.add("");
		}
	}

	private static <T> void serializeConfigValue(List<String> serialized, ConfigValue<T> configValue) {
		String name = configValue.getName();
		IConfigValueSerializer<T> serializer = configValue.getSerializer();

		Component nameComponent = Component.translatable(configValue.getLocalizationKey());
		String localizedName = getLocalizedComment(CONFIG_NAME_KEY, "Name: %s", nameComponent.getString());
		addCommentedStrings(serialized, localizedName);

		Component descriptionComponent = Component.translatable(configValue.getLocalizationKey() + ".description");
		String description = getLocalizedComment(CONFIG_DESCRIPTION_KEY, "Description: %s", descriptionComponent.getString());
		addCommentedStrings(serialized, description);

		String validValues = getLocalizedComment(CONFIG_VALUE_VALUES_KEY, "Valid Values: %s", serializer.getValidValuesDescription());
		addCommentedStrings(serialized, validValues);

		T defaultValue = configValue.getDefaultValue();
		String defaultValueSerialized = serializer.serialize(defaultValue);
		String defaultValueString = getLocalizedComment(CONFIG_DEFAULT_VALUE_KEY, "Default Value: %s", defaultValueSerialized);
		addCommentedStrings(serialized, defaultValueString);

		T value = configValue.getValue();
		String valueString = serializer.serialize(value);
		serialized.add("\t%s = %s".formatted(name, valueString));
	}

	private static String getLocalizedComment(String translationKey, String fallbackFormat, String value) {
		if (Language.getInstance().has(translationKey)) {
			return Component.translatable(translationKey, value).getString();
		}
		return fallbackFormat.formatted(value);
	}

	private static void addCommentedStrings(List<String> serialized, String comment) {
		String[] lines = comment.split("\n");
		if (lines.length == 0) {
			return;
		}
		serialized.add("\t# %s".formatted(lines[0]));
		if (lines.length > 1) {
			for (int i = 1; i < lines.length; i++) {
				serialized.add("\t# %s".formatted(lines[i]));
			}
		}
	}
}
