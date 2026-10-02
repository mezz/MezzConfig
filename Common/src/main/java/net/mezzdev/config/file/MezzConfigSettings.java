package net.mezzdev.config.file;

import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.value.editor.ConfigValueRestartRequirement;
import net.mezzdev.config.schema.ConfigCategory;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchemaDefinition;
import net.mezzdev.config.schema.LayeredConfigSchemaPathResolver;
import net.mezzdev.config.schema.StaticConfigSchemaPathResolver;
import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.deduplicatingrunner.DelayedExecutor;
import net.mezzdev.deduplicatingrunner.DelayedTaskScheduler;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@ApiStatus.Internal
public final class MezzConfigSettings {
	private static final String MOD_ID = "mezz_config";
	private static final String CONFIG_FILE_NAME = "settings.ini";
	private static final String LOCALIZATION_PATH = "mezz_config.config";

	private MezzConfigSettings() {

	}

	public static ConfigManager createManager(
		String fileWatcherThreadName,
		Path configRootDir,
		boolean developmentEnvironment
	) {
		configRootDir = ErrorUtil.checkNotNull(configRootDir, "configRootDir")
			.toAbsolutePath()
			.normalize();
		StartupSettings startupSettings = readStartupSettings(configRootDir, developmentEnvironment);
		DelayedExecutor saveExecutor = ConfigManager.createSaveExecutor();
		ConfigManager configManager = new ConfigManager(
			fileWatcherThreadName,
			startupSettings.fileWatcherSettings(),
			ConfigFileWatcherSettings.serverDefaults(),
			startupSettings.logUntranslatedKeys(),
			saveExecutor
		);
		ConfigSchemaDefinition definition = createDefinition(configRootDir, developmentEnvironment, saveExecutor);
		configManager.registerSchema(definition);
		return configManager;
	}

	static StartupSettings readStartupSettings(Path configRootDir, boolean developmentEnvironment) {
		SettingsValues values = createValues(developmentEnvironment);
		List<ConfigCategory> categories = values.categoryBuilders().stream()
			.map(ConfigCategoryBuilder::build)
			.toList();
		Path clientConfigDirectory = configRootDir.resolve(MOD_ID).resolve("client");
		loadSettingsFile(clientConfigDirectory.resolve("default").resolve(CONFIG_FILE_NAME), categories);
		loadSettingsFile(clientConfigDirectory.resolve(CONFIG_FILE_NAME), categories);
		return values.startupSettings();
	}

	private static void loadSettingsFile(Path path, List<ConfigCategory> categories) {
		if (Files.exists(path)) {
			try {
				ConfigSerializer.loadWithoutNotifyingUnconditionally(path, categories, ConfigSerializer.INSTALLATION_SETTINGS);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to load startup settings: " + path, e);
			}
		}
	}

	private static ConfigSchemaDefinition createDefinition(
		Path configRootDir,
		boolean developmentEnvironment,
		DelayedTaskScheduler saveScheduler
	) {
		saveScheduler = ErrorUtil.checkNotNull(saveScheduler, "saveScheduler");
		SettingsValues values = createValues(developmentEnvironment);
		Path clientConfigDirectory = configRootDir.resolve(MOD_ID).resolve("client");
		return new ConfigSchemaDefinition(
			CONFIG_FILE_NAME,
			MOD_ID,
			new LayeredConfigSchemaPathResolver(
				clientConfigDirectory.resolve("default").resolve(CONFIG_FILE_NAME),
				new StaticConfigSchemaPathResolver(clientConfigDirectory.resolve(CONFIG_FILE_NAME))
			),
			values.categoryBuilders(),
			List.copyOf(values.categoryBuilders()),
			saveScheduler,
			ConfigSchemaType.CLIENT,
			null,
			null
		);
	}

	private static SettingsValues createValues(boolean developmentEnvironment) {
		ConfigFileWatcherSettings defaults = ConfigFileWatcherSettings.clientDefaults();
		ConfigCategoryBuilder fileWatcher = new ConfigCategoryBuilder(LOCALIZATION_PATH, "fileWatcher");
		ConfigValue<Boolean> enabled = fileWatcher.addBoolean("enabled", defaults.enabled())
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		ConfigValue<Long> settlingDelay = fileWatcher.addLong(
				"changeSettlingDelayMilliseconds",
				defaults.changeSettlingDelay().toMillis(),
				1L,
				Long.MAX_VALUE
			)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		ConfigValue<Long> retryInterval = fileWatcher.addLong(
				"missingDirectoryRetryIntervalMilliseconds",
				defaults.missingDirectoryRetryInterval().toMillis(),
				1L,
				Long.MAX_VALUE
			)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		ConfigCategoryBuilder logging = new ConfigCategoryBuilder(LOCALIZATION_PATH, "logging");
		ConfigValue<Boolean> logUntranslatedKeys = logging.addBoolean("logUntranslatedKeys", developmentEnvironment)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();

		return new SettingsValues(
			List.of(fileWatcher, logging),
			enabled,
			settlingDelay,
			retryInterval,
			logUntranslatedKeys
		);
	}

	record StartupSettings(ConfigFileWatcherSettings fileWatcherSettings, boolean logUntranslatedKeys) {}

	private record SettingsValues(
		List<ConfigCategoryBuilder> categoryBuilders,
		ConfigValue<Boolean> enabled,
		ConfigValue<Long> settlingDelay,
		ConfigValue<Long> retryInterval,
		ConfigValue<Boolean> logUntranslatedKeysValue
	) {
		private StartupSettings startupSettings() {
			return new StartupSettings(
				new ConfigFileWatcherSettings(
					enabled.getPendingValue(),
					Duration.ofMillis(settlingDelay.getPendingValue()),
					Duration.ofMillis(retryInterval.getPendingValue())
				),
				logUntranslatedKeysValue.getPendingValue()
			);
		}
	}
}
