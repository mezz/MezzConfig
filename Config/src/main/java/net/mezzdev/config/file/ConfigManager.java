package net.mezzdev.config.file;

import net.mezzdev.config.files.IConfigFile;
import net.mezzdev.config.files.IConfigManager;

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

	ConfigSaveScheduler getSaveExecutor() {
		return saveExecutor;
	}

	void registerSchema(ConfigSchema configFile) {
		configFile.register(fileWatcher, this);
	}

	@Override
	public void addConfigFile(ConfigSchema configFile) {
		this.configFiles.put(configFile.getPath(), configFile);
	}

	void startWatching() {
		fileWatcher.start();
	}

	@Override
	public Collection<? extends IConfigFile> getConfigFiles() {
		return Collections.unmodifiableCollection(configFiles.values());
	}

}
