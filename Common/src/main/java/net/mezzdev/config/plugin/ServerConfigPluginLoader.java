package net.mezzdev.config.plugin;

import net.mezzdev.config.api.files.ConfigManagers;
import net.mezzdev.config.api.files.IConfigManager;
import net.mezzdev.config.api.plugin.IServerConfigPlugin;
import net.mezzdev.config.api.plugin.IServerConfigRegistration;
import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.file.ConfigFileWatcherSettings;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigSchemaBuilder;
import net.mezzdev.config.schema.ConfigSchemaPathResolver;
import net.mezzdev.config.schema.LayeredConfigSchemaPathResolver;
import net.mezzdev.config.server.ServerConfigKey;
import net.mezzdev.config.server.ServerConfigPathResolver;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.util.ErrorUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads common-side server config plugins without referencing client-only Minecraft classes.
 */
public final class ServerConfigPluginLoader {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Path SERVER_DEFAULT_DIR = Path.of("server", "default");

	private ServerConfigPluginLoader() {

	}

	public static IConfigManager createServerConfigManager(
		String fileWatcherThreadName,
		Path configRootDir,
		List<? extends IServerConfigPlugin> plugins
	) {
		return createServerConfigManager(fileWatcherThreadName, configRootDir, plugins, true);
	}

	public static IConfigManager createIntegratedServerConfigManager(
		String fileWatcherThreadName,
		Path configRootDir,
		List<? extends IServerConfigPlugin> plugins
	) {
		return createServerConfigManager(fileWatcherThreadName, configRootDir, plugins, false);
	}

	private static IConfigManager createServerConfigManager(
		String fileWatcherThreadName,
		Path configRootDir,
		List<? extends IServerConfigPlugin> plugins,
		boolean exposeThroughApi
	) {
		ConfigManager configManager = new ConfigManager(
			fileWatcherThreadName,
			ConfigFileWatcherSettings.defaults(),
			false
		);
		addPlugins(configManager, configRootDir, plugins, true);
		configManager.startWatching();
		ServerConfigRuntime.setServerConfigManager(configManager);
		if (exposeThroughApi) {
			ConfigManagers.setConfigManager(configManager);
		}
		return configManager;
	}

	static void addPlugins(
		ConfigManager configManager,
		Path configRootDir,
		List<? extends IServerConfigPlugin> plugins,
		boolean authoritative
	) {
		for (IServerConfigPlugin plugin : plugins) {
			addPlugin(configManager, configRootDir, plugin, authoritative);
		}
	}

	private static void addPlugin(
		ConfigManager configManager,
		Path configRootDir,
		IServerConfigPlugin plugin,
		boolean authoritative
	) {
		try {
			String modId = validateModId(plugin.getModId());
			Path pluginConfigDir = configRootDir.resolve(modId);
			Path defaultConfigDir = pluginConfigDir.resolve(SERVER_DEFAULT_DIR);
			if (authoritative) {
				Files.createDirectories(defaultConfigDir);
			}
			ServerConfigRegistration registration = new ServerConfigRegistration(
				modId,
				configManager,
				defaultConfigDir,
				authoritative
			);
			plugin.registerServerConfigFiles(registration);
		} catch (IOException | RuntimeException | LinkageError e) {
			LOGGER.error("Failed to load server config plugin: {}", plugin.getClass(), e);
		}
	}

	private static String validateModId(@Nullable String modId) {
		return ConfigSchema.validateModId(modId);
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

	private record ServerConfigRegistration(
		String modId,
		ConfigManager configManager,
		Path defaultConfigDir,
		boolean authoritative
	) implements IServerConfigRegistration {
		@Override
		public IConfigSchemaBuilder createServerSchemaBuilder(String configFileName, String localizationPath) {
			localizationPath = ErrorUtil.checkNotNull(localizationPath, "localizationPath");
			Path relativeConfigFile = getRelativeConfigFile(configFileName);
			String normalizedFileName = relativeConfigFile.toString().replace(File.separatorChar, '/');
			ServerConfigKey key = new ServerConfigKey(modId, normalizedFileName);
			Path defaultConfigFile = defaultConfigDir.resolve(relativeConfigFile).normalize();
			ServerConfigPathResolver activePathResolver = new ServerConfigPathResolver(key, relativeConfigFile, authoritative);
			ConfigSchemaPathResolver pathResolver = activePathResolver;
			if (authoritative) {
				pathResolver = new LayeredConfigSchemaPathResolver(defaultConfigFile, activePathResolver);
			}
			return new ConfigSchemaBuilder(
				modId,
				pathResolver,
				localizationPath,
				configManager,
				ConfigSchemaType.SERVER,
				key
			);
		}

		@Override
		public IConfigManager getConfigManager() {
			return configManager;
		}
	}
}
