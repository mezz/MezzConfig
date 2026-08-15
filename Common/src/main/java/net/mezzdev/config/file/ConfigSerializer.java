package net.mezzdev.config.file;

import com.google.gson.JsonElement;
import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.mezzdev.config.api.value.IConfigListValueSerializer;
import net.mezzdev.config.api.value.IDeserializeResult;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigSerializer {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String CONFIG_NAME_KEY = "mezz_config.config.name";
	private static final String CONFIG_DESCRIPTION_KEY = "mezz_config.config.description";
	private static final String CONFIG_VALUE_VALUES_KEY = "mezz_config.config.valueValues";
	private static final String CONFIG_DEFAULT_VALUE_KEY = "mezz_config.config.defaultValue";
	private static final String CONFIG_REQUIRES_WORLD_RESTART_KEY = "mezz_config.config.requiresWorldRestart";
	private static final String CONFIG_REQUIRES_GAME_RESTART_KEY = "mezz_config.config.requiresGameRestart";
	private static final Pattern commentRegex = Pattern.compile("\\s*#.*");
	private static final Pattern categoryRegex = Pattern.compile("\\[(?<category>\\w+)]\\s*");
	private static final Pattern keyValueRegex = Pattern.compile("\\s*(?<key>\\w+)\\s*=\\s*(?<value>.*)");
	static final int MAX_CONFIG_FILE_BYTES = ConfigFileReader.MAX_FILE_BYTES;
	static final int MAX_CONFIG_FILE_LINES = ConfigFileReader.MAX_FILE_LINES;
	private static final int MAX_LOGGED_PROBLEMS = 100;
	private static final int MAX_LOGGED_LINE_CHARACTERS = 512;
	private static final int MAX_LOGGED_MESSAGE_CHARACTERS = 4 * 1024;
	private static final int MAX_TRACKED_RECOVERY_ATTEMPTS = 256;
	private static final Map<Path, FileTime> saveTimes = new ConcurrentHashMap<>();
	private static final Map<Path, FailureFingerprint> recoveryAttempts = Collections.synchronizedMap(
		new LinkedHashMap<>() {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Path, FailureFingerprint> eldest) {
				return size() > MAX_TRACKED_RECOVERY_ATTEMPTS;
			}
		}
	);

	private ConfigSerializer() {}

	private static String getLineErrorString(Path path, int lineNumber, String line, String errorMessage) {
		return """
			%s
			Config file: %s
			Line #%s: "%s\"""".formatted(
			summarizeForLog(errorMessage, MAX_LOGGED_MESSAGE_CHARACTERS),
			path,
			lineNumber,
			summarizeForLog(line, MAX_LOGGED_LINE_CHARACTERS)
		);
	}

	public static List<AppliedConfigValueChange<?>> load(
		Path path,
		List<ConfigCategory> categories
	) throws IOException {
		List<AppliedConfigValueChange<?>> changes = loadWithoutNotifying(path, categories);
		return ConfigValue.notifyChangedValues(changes);
	}

	public static List<AppliedConfigValueChange<?>> loadWithoutNotifying(
		Path path,
		List<ConfigCategory> categories
	) throws IOException {
		return loadWithoutNotifying(path, categories, true);
	}

	public static List<AppliedConfigValueChange<?>> loadWithoutNotifyingUnconditionally(
		Path path,
		List<ConfigCategory> categories
	) throws IOException {
		return loadWithoutNotifying(path, categories, false);
	}

	private static List<AppliedConfigValueChange<?>> loadWithoutNotifying(
		Path path,
		List<ConfigCategory> categories,
		boolean skipFilesJustSaved
	) throws IOException {
		if (skipFilesJustSaved) {
			FileTime lastModifiedTime = Files.getLastModifiedTime(path);
			FileTime savedTime = saveTimes.get(path);
			if (savedTime != null && savedTime.compareTo(lastModifiedTime) >= 0) {
				LOGGER.debug("Skipping loading config file, it was just saved by us: {}", path);
				return List.of();
			}
		}

		LOGGER.debug("Loading config file: {}", path);
		ConfigFileReader.Contents contents;
		try {
			contents = ConfigFileReader.read(path);
		} catch (ConfigFileReader.MalformedFileException e) {
			LOGGER.error("Malformed config file '{}': {}", path, e.getMessage());
			recoverMalformedFile(path, categories, new FailureFingerprint(e.fingerprint()), 1);
			return List.of();
		}
		List<String> lines = contents.lines();

		Map<String, ConfigCategory> categoriesMap = new LinkedHashMap<>();
		for (ConfigCategory category : categories) {
			categoriesMap.put(category.getName(), category);
		}

		List<AppliedConfigValueChange<?>> changes = new ArrayList<>();
		Set<ConfigValue<?>> encounteredValues = Collections.newSetFromMap(new IdentityHashMap<>());
		ProblemTracker problems = new ProblemTracker(path);
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
				if (category == null) {
					if (hasMovedValues(categoryName, categories)) {
						problems.log(lineNumber, line, "Legacy config category '[%s]' will be migrated.".formatted(categoryName));
					} else {
						problems.log(lineNumber, line,
							"""
						'[%s]' is not a valid category name.
						Valid names are: [%s]
						Skipping all values until the first valid category is declared."""
								.formatted(
									categoryName,
									String.join(", ", categoriesMap.keySet())
								)
						);
					}
				}
				continue;
			}
			if (line.stripLeading().startsWith("[")) {
				categoryName = "";
				category = null;
				problems.log(lineNumber, line, "Encountered an invalid category declaration; following values are ignored until a valid category is declared.");
				continue;
			}
			if (categoryName.isEmpty()) {
				problems.log(lineNumber, line, """
				Expected a '[category]' here.
				Configs must start with a category before defining values.
				Skipping all lines until the first valid category is declared.""");
				continue;
			}

			Matcher keyValueMatcher = keyValueRegex.matcher(line);
			if (keyValueMatcher.matches()) {
				final String key = keyValueMatcher.group("key").trim();
				final String encodedValue = keyValueMatcher.group("value").trim();
				IDeserializeResult<JsonElement> decodeResult = ConfigFileValueCodec.deserialize(encodedValue);
				final JsonElement value = decodeResult.getResult().orElse(null);
				if (value == null) {
					problems.log(
						lineNumber,
						line,
						"Invalid encoded config value: " + String.join("\n", decodeResult.getDiagnostics())
					);
					continue;
				}
				Optional<ConfigValue<?>> configValue = getConfigValue(category, key);
				if (configValue.isEmpty()) {
					ConfigValueReference legacyValueReference = new ConfigValueReference(categoryName, key);
					List<ConfigValueMigration<?>> migrations = getMovedValueMigrations(categories, legacyValueReference);
					if (migrations.isEmpty()) {
						problems.log(lineNumber, line, getUnknownConfigValueError(category, categoryName, key));
					} else {
						problems.log(lineNumber, line, "Legacy config value '%s.%s' will be migrated.".formatted(categoryName, key));
						int previousChangeCount = changes.size();
						List<String> diagnostics = new ArrayList<>();
						String migrationValue = ConfigFileValueAdapter.toPublicSerializerRepresentation(value);
						migrations.forEach(migration -> diagnostics.addAll(migration.migrate(migrationValue, changes)));
						for (int changeIndex = previousChangeCount; changeIndex < changes.size(); changeIndex++) {
							encounteredValues.add(changes.get(changeIndex).configValue());
						}
						if (!diagnostics.isEmpty()) {
							problems.log(lineNumber, line, getDeserializeDiagnostics(migrationValue, diagnostics));
						}
					}
				} else {
					ConfigValue<?> knownValue = configValue.orElseThrow();
					if (!encounteredValues.add(knownValue)) {
						problems.log(lineNumber, line, "Config value '%s.%s' was declared more than once; the last usable value wins."
							.formatted(categoryName, key));
					}
					List<String> diagnostics = setFromConfigFileValue(knownValue, value, changes);
					if (!diagnostics.isEmpty()) {
						problems.log(lineNumber, line, getDeserializeDiagnostics(ConfigFileValueCodec.serialize(value), diagnostics));
					}
				}
			} else {
				problems.log(lineNumber, line,
					"""
						Encountered an invalid line.
						Every line in the config must be either:
						 * a '[category]'
						 * a 'key = value' pair
						 * a '#'-prefixed comment"""
				);
			}
		}
		if (problems.count() > 0) {
			recoverMalformedFile(path, categories, new FailureFingerprint(contents.fingerprint()), problems.count());
		} else {
			recoveryAttempts.remove(path.toAbsolutePath().normalize());
		}
		return List.copyOf(changes);
	}

	private static <T> List<String> setFromConfigFileValue(
		ConfigValue<T> configValue,
		JsonElement value,
		List<AppliedConfigValueChange<?>> changes
	) {
		IDeserializeResult<T> result = ConfigFileValueAdapter.deserialize(configValue.getSerializer(), value);
		return configValue.setFromDeserializedValue(result, changes);
	}

	private static void recoverMalformedFile(
		Path path,
		List<ConfigCategory> categories,
		FailureFingerprint fingerprint,
		int problemCount
	) {
		Path normalizedPath = path.toAbsolutePath().normalize();
		synchronized (recoveryAttempts) {
			if (fingerprint.equals(recoveryAttempts.get(normalizedPath))) {
				LOGGER.warn("Skipping a repeated correction attempt for unchanged malformed config file '{}'.", path);
				return;
			}
			recoveryAttempts.put(normalizedPath, fingerprint);
		}
		try {
			Path backup = ConfigFileUtil.backUpFile(path);
			LOGGER.warn(
				"Correcting malformed config file '{}' after {} problem(s); the original is preserved at '{}'.",
				path,
				problemCount,
				backup
			);
			save(path, categories);
			recoveryAttempts.remove(normalizedPath, fingerprint);
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not safely back up and correct malformed config file '{}'; leaving it unchanged.", path, e);
		}
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

	private static String getUnknownConfigValueError(
		@Nullable ConfigCategory category,
		String categoryName,
		String key
	) {
		if (category == null) {
			return """
				'%s' is not a valid config category.
				Skipping this key."""
				.formatted(categoryName);
		}
		return """
			'%s' is not a valid config key for config category '%s'.
			Valid keys: [%s]
			Skipping this key."""
			.formatted(
				key, category.getName(),
				String.join(", ", category.getValueNames())
			);
	}

	private static String getDeserializeDiagnostics(
		String value,
		List<String> diagnostics
	) {
		StringBuilder diagnosticSummary = new StringBuilder();
		for (String diagnostic : diagnostics) {
			if (!diagnosticSummary.isEmpty()) {
				diagnosticSummary.append('\n');
			}
			int remainingCharacters = MAX_LOGGED_MESSAGE_CHARACTERS - diagnosticSummary.length();
			if (remainingCharacters <= 0) {
				break;
			}
			diagnosticSummary.append(summarizeForLog(diagnostic, remainingCharacters));
		}
		return """
			Encountered diagnostics when deserializing value '%s':
			%s""".formatted(
			summarizeForLog(value, MAX_LOGGED_LINE_CHARACTERS),
			diagnosticSummary
		);
	}

	private static String summarizeForLog(String value, int maxCharacters) {
		if (value.length() <= maxCharacters) {
			return value;
		}
		if (maxCharacters <= 1) {
			return "…";
		}
		return value.substring(0, maxCharacters - 1) + "…";
	}

	private record FailureFingerprint(String value) {}

	private static final class ProblemTracker {
		private final Path path;
		private int count;

		private ProblemTracker(Path path) {
			this.path = path;
		}

		private void log(int lineNumber, String line, String message) {
			count++;
			if (count <= MAX_LOGGED_PROBLEMS) {
				LOGGER.error(getLineErrorString(path, lineNumber, line, message));
			} else if (count == MAX_LOGGED_PROBLEMS + 1) {
				LOGGER.error("Config file '{}' has more than {} problems; suppressing further per-line diagnostics.", path, MAX_LOGGED_PROBLEMS);
			}
		}

		private int count() {
			return count;
		}
	}

	public static void save(Path path, List<ConfigCategory> categories) throws IOException {
		save(path, categories, false);
	}

	public static void saveDefaults(Path path, List<ConfigCategory> categories) throws IOException {
		save(path, categories, true);
	}

	private static void save(Path path, List<ConfigCategory> categories, boolean saveDefaults) throws IOException {
		List<String> serialized = new ArrayList<>();
		categories.forEach(category -> {
			serializeCategory(serialized, category, saveDefaults);
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
			language.has(CONFIG_DEFAULT_VALUE_KEY) &&
			language.has(CONFIG_REQUIRES_WORLD_RESTART_KEY) &&
			language.has(CONFIG_REQUIRES_GAME_RESTART_KEY);
	}

	private static void serializeCategory(List<String> serialized, ConfigCategory category, boolean saveDefaults) {
		addLocalizedNameAndDescription(serialized, category.getLocalizationKey(), "");
		serialized.add("[%s]".formatted(category.getName()));
		for (ConfigValue<?> value : category.getConfigValues()) {
			serializeConfigValue(serialized, value, saveDefaults);
			serialized.add("");
		}
	}

	private static <T> void serializeConfigValue(
		List<String> serialized,
		ConfigValue<T> configValue,
		boolean saveDefaults
	) {
		String name = configValue.getName();
		IConfigValueSerializer<T> serializer = configValue.getSerializer();

		addLocalizedNameAndDescription(serialized, configValue.getLocalizationKey(), "\t");

		String validValues = getLocalizedComment(CONFIG_VALUE_VALUES_KEY, "Valid Values: %s", getConfigFileValidValuesDescription(serializer));
		addCommentedStrings(serialized, validValues);

		T defaultValue = configValue.getDefaultValue();
		String defaultValueSerialized = ConfigFileValueAdapter.serialize(serializer, defaultValue);
		String defaultValueString = getLocalizedComment(CONFIG_DEFAULT_VALUE_KEY, "Default Value: %s", defaultValueSerialized);
		addCommentedStrings(serialized, defaultValueString);

		addRestartRequirementComment(serialized, configValue);

		T value = defaultValue;
		if (!saveDefaults) {
			value = configValue.getValueWithoutLoading();
		}
		String valueString = ConfigFileValueAdapter.serialize(serializer, value);
		serialized.add("\t%s = %s".formatted(name, valueString));
	}

	private static String getConfigFileValidValuesDescription(IConfigValueSerializer<?> serializer) {
		if (serializer instanceof IConfigListValueSerializer<?> listSerializer) {
			return "A bracketed list containing values of:\n%s".formatted(
				getConfigFileValidValuesDescription(listSerializer.getElementSerializer())
			);
		}
		return serializer.getValidValuesDescription();
	}

	private static void addLocalizedNameAndDescription(List<String> serialized, String localizationKey, String indentation) {
		Component nameComponent = Component.translatable(localizationKey);
		String localizedName = getLocalizedComment(CONFIG_NAME_KEY, "Name: %s", nameComponent.getString());
		addCommentedStrings(serialized, localizedName, indentation);

		Component descriptionComponent = Component.translatable(localizationKey + ".description");
		String description = getLocalizedComment(CONFIG_DESCRIPTION_KEY, "Description: %s", descriptionComponent.getString());
		addCommentedStrings(serialized, description, indentation);
	}

	private static void addRestartRequirementComment(List<String> serialized, ConfigValue<?> configValue) {
		ConfigValueRestartRequirement restartRequirement = configValue.getRestartRequirement();
		switch (restartRequirement) {
			case NONE -> {}
			case WORLD_RESTART -> {
				String requiresRestart = getLocalizedComment(
					CONFIG_REQUIRES_WORLD_RESTART_KEY,
					"Requires a world restart to take effect."
				);
				addCommentedStrings(serialized, requiresRestart);
			}
			case GAME_RESTART -> {
				String requiresRestart = getLocalizedComment(
					CONFIG_REQUIRES_GAME_RESTART_KEY,
					"Requires a game restart to take effect."
				);
				addCommentedStrings(serialized, requiresRestart);
			}
		}
	}

	private static String getLocalizedComment(String translationKey, String fallback) {
		if (Language.getInstance().has(translationKey)) {
			return Component.translatable(translationKey).getString();
		}
		return fallback;
	}

	private static String getLocalizedComment(String translationKey, String fallbackFormat, String value) {
		if (Language.getInstance().has(translationKey)) {
			return Component.translatable(translationKey, value).getString();
		}
		return fallbackFormat.formatted(value);
	}

	private static void addCommentedStrings(List<String> serialized, String comment) {
		addCommentedStrings(serialized, comment, "\t");
	}

	private static void addCommentedStrings(List<String> serialized, String comment, String indentation) {
		String[] lines = comment.split("\n");
		if (lines.length == 0) {
			return;
		}
		serialized.add("%s# %s".formatted(indentation, lines[0]));
		if (lines.length > 1) {
			for (int i = 1; i < lines.length; i++) {
				serialized.add("%s# %s".formatted(indentation, lines[i]));
			}
		}
	}
}
