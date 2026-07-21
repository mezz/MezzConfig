package net.mezzdev.config;

import net.mezzdev.config.file.ConfigSchema;
import net.mezzdev.config.file.IConfigFileRegistrar;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager implements IJeiConfigManager, IConfigFileRegistrar {
	private final Map<Path, ConfigSchema> configFiles = new HashMap<>();

	public ConfigManager() {
	}

	@Override
	public void registerConfigFile(ConfigSchema configFile) {
		this.configFiles.put(configFile.getPath(), configFile);
	}

	@Override
	public Collection<IJeiConfigFile> getConfigFiles() {
		return Collections.unmodifiableCollection(configFiles.values());
	}

	public void onJeiStarted() {
		configFiles.values().forEach(ConfigSchema::markDirty);
	}
}
