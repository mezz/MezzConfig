package net.mezzdev.config.file;

import net.mezzdev.config.IConfigFile;
import net.mezzdev.config.IConfigManager;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager implements IConfigManager, IConfigFileRegistrar {
	private final Map<Path, ConfigSchema> configFiles = new HashMap<>();

	public ConfigManager() {
	}

	@Override
	public void registerConfigFile(ConfigSchema configFile) {
		this.configFiles.put(configFile.getPath(), configFile);
	}

	@Override
	public Collection<IConfigFile> getConfigFiles() {
		return Collections.unmodifiableCollection(configFiles.values());
	}

	public void onConfigsLoaded() {
		configFiles.values().forEach(ConfigSchema::markDirty);
	}
}
