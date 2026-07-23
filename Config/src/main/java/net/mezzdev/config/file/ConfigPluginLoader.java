package net.mezzdev.config.file;

import net.mezzdev.config.files.IConfigManager;
import net.mezzdev.config.plugin.IConfigPlugin;
import net.mezzdev.config.plugin.IConfigRegistration;
import net.mezzdev.config.schema.IConfigEditableSchema;
import net.mezzdev.config.schema.IConfigSchemaBuilder;
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
		if (modId == null) {
			throw new NullPointerException("modId must not be null.");
		}
		if (modId.isBlank()) {
			throw new IllegalArgumentException("modId must not be blank.");
		}
		return modId;
	}

	private static Path resolveConfigFile(Path pluginConfigDir, String configFileName) {
		if (configFileName == null) {
			throw new NullPointerException("configFileName must not be null.");
		}
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
			if (localizationPath == null) {
				throw new NullPointerException("localizationPath must not be null.");
			}
			Path configFile = resolveConfigFile(pluginConfigDir, configFileName);
			return new ConfigSchemaBuilder(configFile, localizationPath, configManager);
		}

		@Override
		public void registerConfigScreen(Component title, IConfigEditableSchema schema, Runnable restartHandler) {
			if (title == null) {
				throw new NullPointerException("title must not be null.");
			}
			if (schema == null) {
				throw new NullPointerException("schema must not be null.");
			}
			if (restartHandler == null) {
				throw new NullPointerException("restartHandler must not be null.");
			}
			configManager.registerConfigScreen(modId, title, schema, restartHandler);
		}

		@Override
		public IConfigManager getConfigManager() {
			return configManager;
		}
	}
}
