package net.mezzdev.config.file;

import net.mezzdev.config.files.IConfigFile;
import net.mezzdev.config.files.IConfigFileManager;
import net.mezzdev.config.schema.IConfigSchema;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager implements IConfigFileManager, IConfigFileRegistrar {
	private final FileWatcher fileWatcher;
	private final Map<Path, IConfigSchema> configFiles = new HashMap<>();

	public ConfigManager() {
		this("Config File Watcher");
	}

	public ConfigManager(String fileWatcherThreadName) {
		this.fileWatcher = new FileWatcher(fileWatcherThreadName);
	}

	@Override
	public void registerConfigFile(ConfigSchema configFile) {
		this.configFiles.put(configFile.getPath(), configFile);
	}

	@Override
	public void registerConfigFile(IConfigSchema configFile) {
		if (configFile instanceof ConfigSchema configSchema) {
			configSchema.register(fileWatcher, this);
			return;
		}
		this.configFiles.put(configFile.getPath(), configFile);
	}

	@Override
	public void startWatching() {
		fileWatcher.start();
	}

	@Override
	public Collection<? extends IConfigFile> getConfigFiles() {
		return Collections.unmodifiableCollection(configFiles.values());
	}

	@Override
	public void saveAll() {
		configFiles.values().forEach(IConfigSchema::markDirty);
	}
}
