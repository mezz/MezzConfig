package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.file.ConfigSerializer;
import net.mezzdev.config.file.IConfigFileRegistrar;
import net.mezzdev.deduplicatingrunner.DeduplicatingRunner;
import net.mezzdev.deduplicatingrunner.DelayedTaskScheduler;
import net.mezzdev.filewatcher.FileWatcher;
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
	private static final int LOCALIZATION_SAVE_RETRY_LIMIT = 30;

	private final Path path;
	private final List<ConfigCategory> categories;
	private final AtomicBoolean needsLoad = new AtomicBoolean(true);
	private final DeduplicatingRunner delayedSave;

	public ConfigSchema(
		Path path,
		List<ConfigCategoryBuilder> categoryBuilders,
		DelayedTaskScheduler scheduler
	) {
		this.path = path;
		this.categories = categoryBuilders.stream()
			.map(b -> b.build(this))
			.toList();
		this.delayedSave = new DeduplicatingRunner(SAVE_DELAY_TIME, scheduler);
	}

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

	public void register(FileWatcher fileWatcher, IConfigFileRegistrar configFileRegistrar) {
		if (Files.exists(path)) {
			loadIfNeeded();
		}
		saveAfterLocalizationLoads(0);

		fileWatcher.addCallback(path, this::onFileChanged);
		configFileRegistrar.addConfigFile(this);
	}

	private void saveAfterLocalizationLoads(int attempt) {
		if (ConfigSerializer.canLocalizeComments()) {
			save();
			return;
		}
		if (attempt == 0) {
			LOGGER.debug("Localization has not loaded yet, waiting to save the config file: {}", path);
		}
		if (attempt >= LOCALIZATION_SAVE_RETRY_LIMIT) {
			LOGGER.debug("Localization did not load before the config save retry limit, saving with translation keys: {}", path);
			save();
			return;
		}
		delayedSave.run(() -> saveAfterLocalizationLoads(attempt + 1));
	}

	private void save() {
		try {
			ConfigSerializer.save(path, categories);
		} catch (IOException e) {
			LOGGER.error("Failed to save config file: '{}'", path, e);
		}
	}

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
