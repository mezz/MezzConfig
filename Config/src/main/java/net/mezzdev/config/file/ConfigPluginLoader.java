package net.mezzdev.config.file;

import net.mezzdev.config.files.IConfigFileManager;
import net.mezzdev.config.plugin.IConfigPlugin;
import net.mezzdev.config.plugin.IConfigRegistration;
import net.mezzdev.config.files.IConfigSaveScheduler;
import net.mezzdev.config.schema.IConfigSchema;
import net.mezzdev.config.schema.IConfigSchemaBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads discovered config plugins into one config manager.
 */
public final class ConfigPluginLoader {
	private static final Logger LOGGER = LogManager.getLogger();

	private ConfigPluginLoader() {

	}

	public static IConfigFileManager createConfigManager(String fileWatcherThreadName, List<? extends IConfigPlugin> plugins) {
		ConfigManager configManager = new ConfigManager(fileWatcherThreadName);
		Set<String> modIds = new HashSet<>();
		for (IConfigPlugin plugin : plugins) {
			addPlugin(configManager, modIds, plugin);
		}
		configManager.startWatching();
		return configManager;
	}

	private static void addPlugin(ConfigManager configManager, Set<String> modIds, IConfigPlugin plugin) {
		try {
			String modId = validateModId(plugin.getModId());
			if (!modIds.add(modId)) {
				LOGGER.error("Duplicate config plugin for mod id: {}", modId);
				return;
			}
			ConfigRegistration registration = new ConfigRegistration(configManager);
			plugin.registerConfigFiles(registration);
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("Failed to load config plugin: {}", plugin.getClass(), e);
		}
	}

	private static String validateModId(@Nullable String modId) {
		if (modId == null) {
			throw new NullPointerException("modId must not be null.");
		}
		if (modId.isBlank()) {
			throw new IllegalArgumentException("modId must not be blank.");
		}
		return modId;
	}

	private record ConfigRegistration(
		IConfigFileManager configManager
	) implements IConfigRegistration {
		@Override
		public IConfigSchemaBuilder createSchemaBuilder(Path configFile, String localizationPath, IConfigSaveScheduler scheduler) {
			if (configFile == null) {
				throw new NullPointerException("configFile must not be null.");
			}
			if (localizationPath == null) {
				throw new NullPointerException("localizationPath must not be null.");
			}
			if (scheduler == null) {
				throw new NullPointerException("scheduler must not be null.");
			}
			return new ConfigSchemaBuilder(configFile, localizationPath, scheduler);
		}

		@Override
		public void registerConfigFile(IConfigSchema configFile) {
			if (configFile == null) {
				throw new NullPointerException("configFile must not be null.");
			}
			configManager.registerConfigFile(configFile);
		}

		@Override
		public IConfigFileManager getConfigManager() {
			return configManager;
		}
	}
}
