package net.mezzdev.config.file;

import net.mezzdev.config.files.IConfigFile;
import net.mezzdev.config.files.IConfigManager;
import net.mezzdev.config.schema.IConfigEditableSchema;
import net.mezzdev.config.screen.IConfigScreenConfig;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigManager implements IConfigManager, IConfigFileRegistrar {
	private final FileWatcher fileWatcher;
	private final ConfigSaveExecutor saveExecutor;
	private final Map<Path, ConfigSchema> configFiles = new HashMap<>();
	private final Map<String, ConfigScreenConfig> configScreens = new LinkedHashMap<>();

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

	void registerConfigScreen(
		String modId,
		Component title,
		IConfigEditableSchema schema,
		Runnable restartHandler
	) {
		ConfigScreenConfig configScreen = new ConfigScreenConfig(modId, title, schema, restartHandler);
		@Nullable ConfigScreenConfig previous = this.configScreens.putIfAbsent(modId, configScreen);
		if (previous != null) {
			throw new IllegalStateException("A config screen has already been registered for mod id: " + modId);
		}
	}

	void startWatching() {
		fileWatcher.start();
	}

	@Override
	public Collection<? extends IConfigFile> getConfigFiles() {
		return Collections.unmodifiableCollection(configFiles.values());
	}

	@Override
	public Collection<? extends IConfigScreenConfig> getConfigScreenConfigs() {
		return Collections.unmodifiableCollection(configScreens.values());
	}
}
