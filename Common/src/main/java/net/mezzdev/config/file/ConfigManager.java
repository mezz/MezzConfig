package net.mezzdev.config.file;

import net.mezzdev.config.api.files.IConfigFile;
import net.mezzdev.config.api.files.IConfigManager;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.deduplicatingrunner.DelayedTaskScheduler;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager implements IConfigManager, IConfigFileRegistrar {
	private final FileWatcher fileWatcher;
	private final ConfigSaveExecutor saveExecutor;
	private final Map<Path, ConfigSchema> configFiles = new HashMap<>();

	public ConfigManager() {
		this("Config File Watcher");
	}

	public ConfigManager(String fileWatcherThreadName) {
		this.fileWatcher = new FileWatcher(fileWatcherThreadName);
		this.saveExecutor = new ConfigSaveExecutor("Mezz Config Save Scheduler");
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
	public Collection<? extends IConfigFile> getConfigFiles() {
		return Collections.unmodifiableCollection(configFiles.values());
	}
}
