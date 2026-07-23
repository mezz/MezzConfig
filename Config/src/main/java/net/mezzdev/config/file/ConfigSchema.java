package net.mezzdev.config.file;

import net.mezzdev.config.value.ConfigValueChange;
import net.mezzdev.config.value.ConfigValueUpdateType;
import net.mezzdev.config.value.IConfigValue;
import net.minecraft.locale.Language;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConfigSchema implements IConfigSchema {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Duration SAVE_DELAY_TIME = Duration.ofSeconds(2);

	private final Path path;
	private final String localizationPath;
	private final List<ConfigCategory> categories;
	private final List<ConfigDisplayCategory> displayCategories;
	private final AtomicBoolean needsLoad = new AtomicBoolean(true);
	private final ConfigSaveRunner delayedSave;

	public ConfigSchema(
		Path path,
		String localizationPath,
		List<ConfigCategoryBuilder> categoryBuilders,
		List<ConfigDisplayCategory> displayCategories,
		net.mezzdev.config.files.IConfigSaveScheduler scheduler
	) {
		this.path = path;
		this.localizationPath = localizationPath;
		this.categories = categoryBuilders.stream()
			.map(b -> b.build(this))
			.toList();
		this.displayCategories = createDisplayCategories(localizationPath, displayCategories, categories);
		this.delayedSave = new ConfigSaveRunner(SAVE_DELAY_TIME, scheduler);
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
				ConfigSerializer.load(path, categories);
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
		save();

		fileWatcher.addCallback(path, this::onFileChanged);
		configFileRegistrar.registerConfigFile(this);
	}

	private void save() {
		if (!Language.getInstance().has(localizationPath)) {
			LOGGER.debug("Localization has not loaded yet, waiting to save the config file: {}", path);
			return;
		}
		try {
			ConfigSerializer.save(path, categories);
		} catch (IOException e) {
			LOGGER.error("Failed to save config file: '{}'", path, e);
		}
	}

	@Override
	public void markDirty() {
		delayedSave.run(this::save);
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
