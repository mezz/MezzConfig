package net.mezzdev.config.plugin;

import net.mezzdev.config.api.files.ConfigManagers;
import net.mezzdev.config.api.files.IConfigManager;
import net.mezzdev.config.api.plugin.IConfigPlugin;
import net.mezzdev.config.api.plugin.IConfigRegistration;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.sorting.ISortingConfig;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.file.MezzConfigSettings;
import net.mezzdev.config.schema.ClientWorldConfigSchemaPathResolver;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigSchemaBuilder;
import net.mezzdev.config.sorting.SortingConfig;
import net.mezzdev.config.util.ErrorUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Loads discovered config plugins into one config manager.
 */
public final class ConfigPluginLoader {
	private static final Logger LOGGER = LogManager.getLogger();

	private ConfigPluginLoader() {

	}

	public static IConfigManager createConfigManager(
		String fileWatcherThreadName,
		Path configRootDir,
		boolean developmentEnvironment,
		List<? extends IConfigPlugin> plugins
	) {
		MezzConfigSettings settings = MezzConfigSettings.load(configRootDir, developmentEnvironment);
		ConfigManager configManager = new ConfigManager(
			fileWatcherThreadName,
			settings.fileWatcherSettings(),
			settings.logUntranslatedKeys()
		);
		MezzConfigSettings.registerSchema(configManager, configRootDir, developmentEnvironment);
		for (IConfigPlugin plugin : plugins) {
			addPlugin(configManager, configRootDir, plugin);
		}
		configManager.startWatching();
		ConfigManagers.setConfigManager(configManager);
		return configManager;
	}

	private static void addPlugin(ConfigManager configManager, Path configRootDir, IConfigPlugin plugin) {
		try {
			String modId = validateModId(plugin.getModId());
			Path pluginConfigDir = configRootDir.resolve(modId);
			Files.createDirectories(pluginConfigDir);
			ConfigRegistration registration = new ConfigRegistration(modId, configManager, pluginConfigDir);
			plugin.registerConfigFiles(registration);
		} catch (IOException | RuntimeException | LinkageError e) {
			LOGGER.error("Failed to load config plugin: {}", plugin.getClass(), e);
		}
	}

	private static String validateModId(@Nullable String modId) {
		return ConfigSchema.validateModId(modId);
	}

	private static Path resolveConfigFile(Path pluginConfigDir, String configFileName) {
		Path relativeConfigFile = getRelativeConfigFile(configFileName);
		return pluginConfigDir.resolve(relativeConfigFile).normalize();
	}

	private static Path getRelativeConfigFile(String configFileName) {
		configFileName = ErrorUtil.checkNotNull(configFileName, "configFileName");
		if (configFileName.isBlank()) {
			throw new IllegalArgumentException("configFileName must not be blank.");
		}
		Path relativeConfigFile = Path.of(configFileName).normalize();
		if (relativeConfigFile.isAbsolute() || relativeConfigFile.startsWith("..")) {
			throw new IllegalArgumentException("configFileName must be a relative path inside the plugin config directory: " + configFileName);
		}
		return relativeConfigFile;
	}

	private record ConfigRegistration(
		String modId,
		ConfigManager configManager,
		Path pluginConfigDir
	) implements IConfigRegistration {
		@Override
		public IConfigSchemaBuilder createSchemaBuilder(String configFileName, String localizationPath) {
			localizationPath = ErrorUtil.checkNotNull(localizationPath, "localizationPath");
			Path configFile = resolveConfigFile(pluginConfigDir, configFileName);
			return new ConfigSchemaBuilder(modId, configFile, localizationPath, configManager);
		}

		@Override
		public IConfigSchemaBuilder createClientWorldSchemaBuilder(String configFileName, String localizationPath) {
			localizationPath = ErrorUtil.checkNotNull(localizationPath, "localizationPath");
			Path relativeConfigFile = getRelativeConfigFile(configFileName);
			ClientWorldConfigSchemaPathResolver pathResolver = new ClientWorldConfigSchemaPathResolver(
				relativeConfigFile,
				() -> ClientWorldConfigPathUtil.getWorldPath(pluginConfigDir)
			);
			return new ConfigSchemaBuilder(modId, pathResolver, localizationPath, configManager);
		}

		@Override
		public ISortingConfig<String> createSortingConfig(
			String configFileName,
			Comparator<String> defaultSortOrder,
			boolean allowsRemovingValues
		) {
			Path configFile = resolveConfigFile(pluginConfigDir, configFileName);
			return new SortingConfig(configFile, defaultSortOrder, allowsRemovingValues);
		}

		@Override
		public IConfigManager getConfigManager() {
			return configManager;
		}
	}
}
