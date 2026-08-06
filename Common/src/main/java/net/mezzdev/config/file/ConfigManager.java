package net.mezzdev.config.file;

import net.mezzdev.config.api.files.IConfigManager;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.deduplicatingrunner.DelayedExecutor;
import net.mezzdev.deduplicatingrunner.DelayedTaskScheduler;
import net.mezzdev.filewatcher.FileWatcher;
import net.mezzdev.filewatcher.FileWatcherUnavailableException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ConfigManager implements IConfigManager, IConfigFileRegistrar {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Duration SAVE_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
	private static final String SAVE_SCHEDULER_THREAD_NAME = "MezzConfig Save Scheduler";

	private final @Nullable FileWatcher fileWatcher;
	private final DelayedExecutor saveExecutor;
	private final List<ConfigSchema> configFiles = new ArrayList<>();

	public ConfigManager() {
		this("Config File Watcher");
	}

	public ConfigManager(String fileWatcherThreadName) {
		this(fileWatcherThreadName, ConfigFileWatcherSettings.defaults());
	}

	public ConfigManager(String fileWatcherThreadName, ConfigFileWatcherSettings fileWatcherSettings) {
		fileWatcherThreadName = ErrorUtil.checkNotNull(fileWatcherThreadName, "fileWatcherThreadName");
		fileWatcherSettings = ErrorUtil.checkNotNull(fileWatcherSettings, "fileWatcherSettings");
		this.fileWatcher = createFileWatcher(fileWatcherThreadName, fileWatcherSettings);
		this.saveExecutor = new DelayedExecutor(SAVE_SHUTDOWN_TIMEOUT, SAVE_SCHEDULER_THREAD_NAME);
		Runtime.getRuntime()
			.addShutdownHook(new Thread(saveExecutor::shutdown, SAVE_SCHEDULER_THREAD_NAME + " Shutdown"));
	}

	private static @Nullable FileWatcher createFileWatcher(
		String fileWatcherThreadName,
		ConfigFileWatcherSettings fileWatcherSettings
	) {
		if (!fileWatcherSettings.enabled()) {
			LOGGER.info("Automatic config file watching is disabled.");
			return null;
		}
		try {
			return new FileWatcher(
				fileWatcherThreadName,
				fileWatcherSettings.changeSettlingDelay(),
				fileWatcherSettings.missingDirectoryRetryInterval()
			);
		} catch (FileWatcherUnavailableException e) {
			LOGGER.error("Automatic config file watching is unavailable.", e);
			return null;
		}
	}

	public DelayedTaskScheduler getSaveScheduler() {
		return saveExecutor;
	}

	public void registerSchema(ConfigSchema configFile) {
		configFile.register(fileWatcher, this);
	}

	@Override
	public void addConfigFile(ConfigSchema configFile) {
		this.configFiles.add(configFile);
	}

	public void startWatching() {
		if (fileWatcher != null) {
			fileWatcher.start();
		}
	}

	@Override
	public Collection<? extends IConfigSchema> getSchemas() {
		return Collections.unmodifiableCollection(configFiles);
	}
}
