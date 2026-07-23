package net.mezzdev.config.plugin;

import net.mezzdev.config.api.files.IConfigManager;
import net.mezzdev.config.api.plugin.IConfigPlugin;
import net.mezzdev.config.api.plugin.IConfigRegistration;
import net.mezzdev.config.api.schema.IConfigEditableSchema;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.screen.IConfigRestartHandler;
import net.mezzdev.config.api.util.ErrorUtil;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.file.ConfigManagers;
import net.mezzdev.config.schema.ConfigSchemaBuilder;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
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

	public static IConfigManager createConfigManager(String fileWatcherThreadName, Path configRootDir, List<? extends IConfigPlugin> plugins) {
		ConfigManager configManager = new ConfigManager(fileWatcherThreadName);
		Set<String> modIds = new HashSet<>();
		for (IConfigPlugin plugin : plugins) {
			addPlugin(configManager, configRootDir, modIds, plugin);
		}
		configManager.startWatching();
		ConfigManagers.setConfigManager(configManager);
		return configManager;
	}

	private static void addPlugin(ConfigManager configManager, Path configRootDir, Set<String> modIds, IConfigPlugin plugin) {
		try {
			String modId = validateModId(plugin.getModId());
			if (!modIds.add(modId)) {
				LOGGER.error("Duplicate config plugin for mod id: {}", modId);
				return;
			}
			Path pluginConfigDir = configRootDir.resolve(modId);
			Files.createDirectories(pluginConfigDir);
			ConfigRegistration registration = new ConfigRegistration(modId, configManager, pluginConfigDir);
			plugin.registerConfigFiles(registration);
		} catch (IOException | RuntimeException | LinkageError e) {
			LOGGER.error("Failed to load config plugin: {}", plugin.getClass(), e);
		}
	}

	private static String validateModId(@Nullable String modId) {
		modId = ErrorUtil.checkNotNull(modId, "modId");
		if (modId.isBlank()) {
			throw new IllegalArgumentException("modId must not be blank.");
		}
		return modId;
	}

	private static Path resolveConfigFile(Path pluginConfigDir, String configFileName) {
		configFileName = ErrorUtil.checkNotNull(configFileName, "configFileName");
		if (configFileName.isBlank()) {
			throw new IllegalArgumentException("configFileName must not be blank.");
		}
		Path relativeConfigFile = Path.of(configFileName).normalize();
		if (relativeConfigFile.isAbsolute() || relativeConfigFile.startsWith("..")) {
			throw new IllegalArgumentException("configFileName must be a relative path inside the plugin config directory: " + configFileName);
		}
		return pluginConfigDir.resolve(relativeConfigFile).normalize();
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
			return new ConfigSchemaBuilder(configFile, localizationPath, configManager);
		}

		@Override
		public void registerConfigScreen(Component title, IConfigEditableSchema schema, IConfigRestartHandler restartHandler) {
			title = ErrorUtil.checkNotNull(title, "title");
			schema = ErrorUtil.checkNotNull(schema, "schema");
			restartHandler = ErrorUtil.checkNotNull(restartHandler, "restartHandler");
			configManager.registerConfigScreen(modId, title, schema, restartHandler);
		}

		@Override
		public IConfigManager getConfigManager() {
			return configManager;
		}
	}
}
