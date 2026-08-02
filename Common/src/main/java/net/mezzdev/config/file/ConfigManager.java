package net.mezzdev.config.file;

import net.mezzdev.config.api.files.IConfigManager;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.deduplicatingrunner.DelayedExecutor;
import net.mezzdev.deduplicatingrunner.DelayedTaskScheduler;
import net.mezzdev.filewatcher.FileWatcher;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager implements IConfigManager, IConfigFileRegistrar {
	private static final Duration SAVE_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
	private static final String SAVE_SCHEDULER_THREAD_NAME = "MezzConfig Save Scheduler";

	private final FileWatcher fileWatcher;
	private final DelayedExecutor saveExecutor;
	private final Map<Path, ConfigSchema> configFiles = new HashMap<>();

	public ConfigManager() {
		this("Config File Watcher");
	}

	public ConfigManager(String fileWatcherThreadName) {
		this.fileWatcher = new FileWatcher(fileWatcherThreadName);
		this.saveExecutor = new DelayedExecutor(SAVE_SHUTDOWN_TIMEOUT, SAVE_SCHEDULER_THREAD_NAME);
		Runtime.getRuntime()
			.addShutdownHook(new Thread(saveExecutor::shutdown, SAVE_SCHEDULER_THREAD_NAME + " Shutdown"));
	}

	public DelayedTaskScheduler getSaveScheduler() {
		return saveExecutor;
	}

	public void registerSchema(ConfigSchema configFile) {
		configFile.register(fileWatcher, this);
	}

	@Override
	public void addConfigFile(ConfigSchema configFile) {
		this.configFiles.put(configFile.getPath(), configFile);
	}

	public void startWatching() {
		fileWatcher.start();
	}

	@Override
	public Collection<? extends IConfigSchema> getConfigFiles() {
		return Collections.unmodifiableCollection(configFiles.values());
	}
}
