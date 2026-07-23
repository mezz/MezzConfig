package net.mezzdev.config.file;

import net.mezzdev.config.api.files.IConfigFile;
import net.mezzdev.config.api.files.IConfigManager;
import net.mezzdev.config.api.schema.IConfigEditableSchema;
import net.mezzdev.config.api.screen.IConfigRestartHandler;
import net.mezzdev.config.api.screen.IConfigScreenConfig;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.screen.ConfigScreenConfig;
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

	public ConfigSaveScheduler getSaveScheduler() {
		return saveExecutor;
	}

	public void registerSchema(ConfigSchema configFile) {
		configFile.register(fileWatcher, this);
	}

	@Override
	public void addConfigFile(ConfigSchema configFile) {
		this.configFiles.put(configFile.getPath(), configFile);
	}

	public void registerConfigScreen(
		String modId,
		Component title,
		IConfigEditableSchema schema,
		IConfigRestartHandler restartHandler
	) {
		ConfigScreenConfig configScreen = new ConfigScreenConfig(modId, title, schema, restartHandler);
		@Nullable ConfigScreenConfig previous = this.configScreens.putIfAbsent(modId, configScreen);
		if (previous != null) {
			throw new IllegalStateException("A config screen has already been registered for mod id: " + modId);
		}
	}

	public void startWatching() {
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
