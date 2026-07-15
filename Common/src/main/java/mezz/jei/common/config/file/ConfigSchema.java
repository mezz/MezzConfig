package mezz.jei.common.config.file;

import mezz.jei.api.runtime.config.ConfigValueUpdateType;
import mezz.jei.api.runtime.config.IJeiConfigValue;
import mezz.jei.common.config.ConfigManager;
import mezz.jei.common.util.DeduplicatingRunner;
import net.minecraft.locale.Language;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConfigSchema implements IConfigSchema {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Duration SAVE_DELAY_TIME = Duration.ofSeconds(2);

	private final Path path;
	@Unmodifiable
	private final List<ConfigCategory> categories;
	private final AtomicBoolean needsLoad = new AtomicBoolean(true);
	private final DeduplicatingRunner delayedSave = new DeduplicatingRunner(SAVE_DELAY_TIME);

	public ConfigSchema(Path path, List<ConfigCategoryBuilder> categoryBuilders) {
		this.path = path;
		this.categories = categoryBuilders.stream()
			.map(b -> b.build(this))
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
		List<ConfigValue<?>> changedValues = new ArrayList<>();
		for (ConfigValueChange<?> change : changes) {
			if (isInternalConfigValue(change) && applyInternalChange(change, changedValues)) {
				updateType = max(updateType, change.configValue().getUpdateType());
			}
		}
		for (ConfigValueChange<?> change : changes) {
			if (!isInternalConfigValue(change) && applyExternalChange(change)) {
				updateType = max(updateType, change.configValue().getUpdateType());
			}
		}
		if (!changedValues.isEmpty()) {
			markDirty();
			changedValues.forEach(ConfigValue::notifyListeners);
		}
		return updateType;
	}

	private static ConfigValueUpdateType max(ConfigValueUpdateType first, ConfigValueUpdateType second) {
		if (first == ConfigValueUpdateType.RESTART_JEI || second == ConfigValueUpdateType.RESTART_JEI) {
			return ConfigValueUpdateType.RESTART_JEI;
		}
		if (first == ConfigValueUpdateType.ON_APPLY || second == ConfigValueUpdateType.ON_APPLY) {
			return ConfigValueUpdateType.ON_APPLY;
		}
		return ConfigValueUpdateType.IMMEDIATE;
	}

	private static boolean isInternalConfigValue(ConfigValueChange<?> change) {
		return change.configValue() instanceof ConfigValue<?>;
	}

	private static <T> boolean applyInternalChange(ConfigValueChange<T> change, List<ConfigValue<?>> changedValues) {
		return applyInternalChange(change.configValue(), change.value(), changedValues);
	}

	private static <T> boolean applyExternalChange(ConfigValueChange<T> change) {
		IJeiConfigValue<T> configValue = change.configValue();
		return configValue.set(change.value());
	}

	@SuppressWarnings("unchecked")
	private static <T> boolean applyInternalChange(
		IJeiConfigValue<T> configValue,
		T value,
		List<ConfigValue<?>> changedValues
	) {
		ConfigValue<T> internalConfigValue = (ConfigValue<T>) configValue;
		if (internalConfigValue.setWithoutNotifying(value)) {
			changedValues.add(internalConfigValue);
			return true;
		}
		return false;
	}

	@Override
	public void register(FileWatcher fileWatcher, ConfigManager configManager) {
		if (Files.exists(path)) {
			loadIfNeeded();
		}
		save();

		fileWatcher.addCallback(path, this::onFileChanged);
		configManager.registerConfigFile(this);
	}

	private void save() {
		if (!Language.getInstance().has("jei.config")) {
			LOGGER.debug("Localization has not loaded yet, waiting to save the config file until JEI starts.");
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
	@Unmodifiable
	public List<ConfigCategory> getCategories() {
		return categories;
	}

	@Override
	public Path getPath() {
		return path;
	}
}
