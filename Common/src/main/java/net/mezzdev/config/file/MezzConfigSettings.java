package net.mezzdev.config.file;

import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigSchemaPathResolver;
import net.mezzdev.config.schema.LayeredConfigSchemaPathResolver;
import net.mezzdev.config.schema.StaticConfigSchemaPathResolver;
import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.config.util.PlayerConfigPathUtil;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.deduplicatingrunner.DelayedTaskScheduler;
import net.mezzdev.filewatcher.FileWatcher;
import org.jetbrains.annotations.ApiStatus;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@ApiStatus.Internal
public record MezzConfigSettings(
	ConfigFileWatcherSettings fileWatcherSettings,
	boolean logUntranslatedKeys
) {
	private static final String MOD_ID = "mezz_config";
	private static final String CONFIG_DIRECTORY_NAME = "mezz_config";
	private static final String CONFIG_FILE_NAME = "settings.ini";
	private static final String LOCALIZATION_PATH = "mezz_config.config";
	private static final String FILE_WATCHER_CATEGORY_NAME = "fileWatcher";
	private static final String ENABLED_NAME = "enabled";
	private static final String CHANGE_SETTLING_DELAY_NAME = "changeSettlingDelayMilliseconds";
	private static final String MISSING_DIRECTORY_RETRY_INTERVAL_NAME = "missingDirectoryRetryIntervalMilliseconds";
	private static final String LOGGING_CATEGORY_NAME = "logging";
	private static final String LOG_UNTRANSLATED_KEYS_NAME = "logUntranslatedKeys";
	private static final DelayedTaskScheduler NO_SAVE_SCHEDULER = (command, delay) -> CompletableFuture.completedFuture(null);

	public MezzConfigSettings {
		fileWatcherSettings = ErrorUtil.checkNotNull(fileWatcherSettings, "fileWatcherSettings");
	}

	public static MezzConfigSettings load(Path configRootDir, boolean developmentEnvironment) {
		configRootDir = ErrorUtil.checkNotNull(configRootDir, "configRootDir");
		SchemaData schemaData = createSchema(
			new StaticConfigSchemaPathResolver(getConfigFile(configRootDir)),
			NO_SAVE_SCHEDULER,
			developmentEnvironment
		);
		schemaData.schema()
			.loadIfNeeded();
		return schemaData.settings();
	}

	public static MezzConfigSettings load(
		Path configRootDir,
		UUID playerId,
		boolean developmentEnvironment
	) {
		configRootDir = ErrorUtil.checkNotNull(configRootDir, "configRootDir");
		playerId = ErrorUtil.checkNotNull(playerId, "playerId");
		SchemaData schemaData = createSchema(
			getLayeredPathResolver(configRootDir, playerId),
			NO_SAVE_SCHEDULER,
			developmentEnvironment
		);
		schemaData.schema()
			.loadIfNeeded();
		return schemaData.settings();
	}

	public static ConfigSchema registerSchema(
		ConfigManager configManager,
		Path configRootDir,
		boolean developmentEnvironment
	) {
		configManager = ErrorUtil.checkNotNull(configManager, "configManager");
		configRootDir = ErrorUtil.checkNotNull(configRootDir, "configRootDir");
		SchemaData schemaData = createSchema(
			new StaticConfigSchemaPathResolver(getConfigFile(configRootDir)),
			configManager.getSaveScheduler(),
			developmentEnvironment
		);
		ConfigSchema schema = schemaData.schema();
		configManager.registerSchema(schema);
		return schema;
	}

	public static ConfigSchema registerSchema(
		ConfigManager configManager,
		Path configRootDir,
		UUID playerId,
		boolean developmentEnvironment
	) {
		configManager = ErrorUtil.checkNotNull(configManager, "configManager");
		configRootDir = ErrorUtil.checkNotNull(configRootDir, "configRootDir");
		playerId = ErrorUtil.checkNotNull(playerId, "playerId");
		SchemaData schemaData = createSchema(
			getLayeredPathResolver(configRootDir, playerId),
			configManager.getSaveScheduler(),
			developmentEnvironment
		);
		ConfigSchema schema = schemaData.schema();
		configManager.registerSchema(schema);
		return schema;
	}

	private static SchemaData createSchema(
		ConfigSchemaPathResolver pathResolver,
		DelayedTaskScheduler scheduler,
		boolean developmentEnvironment
	) {
		pathResolver = ErrorUtil.checkNotNull(pathResolver, "pathResolver");
		scheduler = ErrorUtil.checkNotNull(scheduler, "scheduler");
		ConfigCategoryBuilder fileWatcherCategory = new ConfigCategoryBuilder(LOCALIZATION_PATH, FILE_WATCHER_CATEGORY_NAME);
		ConfigValue<Boolean> enabled = fileWatcherCategory.addBoolean(ENABLED_NAME, true)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		ConfigValue<Long> changeSettlingDelayMilliseconds = fileWatcherCategory.addLong(
				CHANGE_SETTLING_DELAY_NAME,
				FileWatcher.DEFAULT_CHANGE_SETTLING_DELAY.toMillis(),
				1L,
				Long.MAX_VALUE
			)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		ConfigValue<Long> missingDirectoryRetryIntervalMilliseconds = fileWatcherCategory.addLong(
				MISSING_DIRECTORY_RETRY_INTERVAL_NAME,
				FileWatcher.DEFAULT_MISSING_DIRECTORY_RETRY_INTERVAL.toMillis(),
				1L,
				Long.MAX_VALUE
			)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();

		ConfigCategoryBuilder loggingCategory = new ConfigCategoryBuilder(LOCALIZATION_PATH, LOGGING_CATEGORY_NAME);
		ConfigValue<Boolean> logUntranslatedKeys = loggingCategory.addBoolean(LOG_UNTRANSLATED_KEYS_NAME, developmentEnvironment)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();

		ConfigSchema schema = new ConfigSchema(
			MOD_ID,
			pathResolver,
			List.of(fileWatcherCategory, loggingCategory),
			scheduler
		);
		return new SchemaData(
			schema,
			enabled,
			changeSettlingDelayMilliseconds,
			missingDirectoryRetryIntervalMilliseconds,
			logUntranslatedKeys
		);
	}

	private static Path getConfigFile(Path configRootDir) {
		return configRootDir.resolve(CONFIG_DIRECTORY_NAME)
			.resolve(CONFIG_FILE_NAME);
	}

	private static ConfigSchemaPathResolver getLayeredPathResolver(Path configRootDir, UUID playerId) {
		Path configDir = configRootDir.resolve(CONFIG_DIRECTORY_NAME);
		Path defaultPath = configDir.resolve(CONFIG_FILE_NAME);
		Path playerPath = PlayerConfigPathUtil.getPlayerConfigDir(configDir, playerId)
			.resolve(CONFIG_FILE_NAME);
		return new LayeredConfigSchemaPathResolver(
			defaultPath,
			new StaticConfigSchemaPathResolver(playerPath)
		);
	}

	private record SchemaData(
		ConfigSchema schema,
		ConfigValue<Boolean> enabled,
		ConfigValue<Long> changeSettlingDelayMilliseconds,
		ConfigValue<Long> missingDirectoryRetryIntervalMilliseconds,
		ConfigValue<Boolean> logUntranslatedKeys
	) {
		private MezzConfigSettings settings() {
			ConfigFileWatcherSettings fileWatcherSettings = new ConfigFileWatcherSettings(
				enabled.getValue(),
				Duration.ofMillis(changeSettlingDelayMilliseconds.getValue()),
				Duration.ofMillis(missingDirectoryRetryIntervalMilliseconds.getValue())
			);
			return new MezzConfigSettings(fileWatcherSettings, logUntranslatedKeys.getValue());
		}
	}
}
