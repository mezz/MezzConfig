package net.mezzdev.config.schema;

import net.mezzdev.config.api.value.ConfigValueChange;
import net.mezzdev.config.api.value.ConfigValueUpdateType;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.file.ConfigSerializer;
import net.mezzdev.config.file.FileWatcher;
import net.mezzdev.config.file.IConfigFileRegistrar;
import net.mezzdev.config.value.ConfigValueMigration;
import net.mezzdev.config.value.ConfigValueReference;
import net.mezzdev.deduplicatingrunner.DelayedTaskScheduler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConfigSchema implements IConfigSchema {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Duration SAVE_DELAY_TIME = Duration.ofSeconds(2);
	private static final int LOCALIZATION_SAVE_RETRY_LIMIT = 30;

	private final Path path;
	private final List<ConfigCategory> categories;
	private final List<ConfigDisplayCategory> displayCategories;
	private final Map<ConfigValueReference, List<ConfigValueMigration<?, ?>>> legacyValueMigrations;
	private final AtomicBoolean needsLoad = new AtomicBoolean(true);
	private final ConfigSaveRunner delayedSave;

	public ConfigSchema(
		Path path,
		String localizationPath,
		List<ConfigCategoryBuilder> categoryBuilders,
		List<ConfigDisplayCategory> displayCategories,
		Map<ConfigValueReference, List<ConfigValueMigration<?, ?>>> legacyValueMigrations,
		DelayedTaskScheduler scheduler
	) {
		this.path = path;
		this.categories = categoryBuilders.stream()
			.map(b -> b.build(this))
			.toList();
		this.displayCategories = createDisplayCategories(localizationPath, displayCategories, categories);
		this.legacyValueMigrations = copyLegacyValueMigrations(legacyValueMigrations);
		this.delayedSave = new ConfigSaveRunner(SAVE_DELAY_TIME, scheduler);
	}

	private static Map<ConfigValueReference, List<ConfigValueMigration<?, ?>>> copyLegacyValueMigrations(
		Map<ConfigValueReference, List<ConfigValueMigration<?, ?>>> legacyValueMigrations
	) {
		Map<ConfigValueReference, List<ConfigValueMigration<?, ?>>> copy = new LinkedHashMap<>();
		legacyValueMigrations.forEach((key, value) -> copy.put(key, List.copyOf(value)));
		return Map.copyOf(copy);
	}

	private static List<ConfigDisplayCategory> createDisplayCategories(
		String localizationPath,
		List<ConfigDisplayCategory> displayCategories,
		List<ConfigCategory> categories
	) {
		if (!displayCategories.isEmpty()) {
			return List.copyOf(displayCategories);
		}
		return categories.stream()
			.map(category -> ConfigDisplayCategory.createWithValues(
				localizationPath + "." + category.getName(),
				category.getName(),
				List.copyOf(category.getConfigValues())
			))
			.toList();
	}

	@Override
	public void loadIfNeeded() {
		if (!needsLoad.compareAndSet(true, false)) {
			return;
		}

		if (Files.exists(path)) {
			try {
				ConfigSerializer.load(path, categories, legacyValueMigrations);
			} catch (IOException e) {
				LOGGER.error("Failed to load config schema for: {}", path, e);
			}
		}
	}

	private void onFileChanged() {
		needsLoad.set(true);
	}

	@Override
	public ConfigValueUpdateType getUpdateType(List<ConfigValueChange<?>> changes) {
		ConfigValueUpdateType updateType = ConfigValueUpdateType.IMMEDIATE;
		for (ConfigValueChange<?> change : changes) {
			updateType = max(updateType, change.configValue().getUpdateType());
		}
		return updateType;
	}

	@Override
	public ConfigValueUpdateType applyChanges(List<ConfigValueChange<?>> changes) {
		ConfigValueUpdateType updateType = ConfigValueUpdateType.IMMEDIATE;
		for (ConfigValueChange<?> change : changes) {
			if (applyChange(change)) {
				updateType = max(updateType, change.configValue().getUpdateType());
			}
		}
		return updateType;
	}

	@Override
	public List<ConfigDisplayCategory> getDisplayCategories() {
		return displayCategories;
	}

	private static ConfigValueUpdateType max(ConfigValueUpdateType first, ConfigValueUpdateType second) {
		if (first == ConfigValueUpdateType.RESTART || second == ConfigValueUpdateType.RESTART) {
			return ConfigValueUpdateType.RESTART;
		}
		if (first == ConfigValueUpdateType.ON_APPLY || second == ConfigValueUpdateType.ON_APPLY) {
			return ConfigValueUpdateType.ON_APPLY;
		}
		return ConfigValueUpdateType.IMMEDIATE;
	}

	private static <T> boolean applyChange(ConfigValueChange<T> change) {
		IConfigValue<T> configValue = change.configValue();
		return configValue.set(change.value());
	}

	@Override
	public void register(FileWatcher fileWatcher, IConfigFileRegistrar configFileRegistrar) {
		if (Files.exists(path)) {
			loadIfNeeded();
		}
		saveAfterLocalizationLoads(0);

		fileWatcher.addCallback(path, this::onFileChanged);
		configFileRegistrar.addConfigFile(this);
	}

	private void saveAfterLocalizationLoads(int attempt) {
		if (save(attempt == 0)) {
			return;
		}
		if (attempt < LOCALIZATION_SAVE_RETRY_LIMIT) {
			delayedSave.run(() -> saveAfterLocalizationLoads(attempt + 1));
		} else {
			LOGGER.debug("Localization did not load before the config save retry limit for: {}", path);
		}
	}

	private boolean save(boolean logMissingLocalization) {
		if (!ConfigSerializer.canLocalizeComments()) {
			if (logMissingLocalization) {
				LOGGER.debug("Localization has not loaded yet, waiting to save the config file: {}", path);
			}
			return false;
		}
		try {
			ConfigSerializer.save(path, categories);
		} catch (IOException e) {
			LOGGER.error("Failed to save config file: '{}'", path, e);
		}
		return true;
	}

	@Override
	public void markDirty() {
		delayedSave.run(() -> saveAfterLocalizationLoads(0));
	}

	@Override
	public void clearListeners() {
		for (ConfigCategory configCategory : categories) {
			configCategory.clearListeners();
		}
	}

	@Override
	public List<ConfigCategory> getCategories() {
		return categories;
	}

	@Override
	public Path getPath() {
		return path;
	}
}
