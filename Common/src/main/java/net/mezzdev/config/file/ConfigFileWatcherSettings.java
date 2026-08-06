package net.mezzdev.config.file;

import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.deduplicatingrunner.DelayedTaskScheduler;
import net.mezzdev.filewatcher.FileWatcher;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public record ConfigFileWatcherSettings(
	boolean enabled,
	Duration changeSettlingDelay,
	Duration missingDirectoryRetryInterval
) {
	private static final String CONFIG_DIRECTORY_NAME = "mezz_config";
	private static final String CONFIG_FILE_NAME = "file_watcher.ini";
	private static final String LOCALIZATION_PATH = "mezz_config.config";
	private static final String CATEGORY_NAME = "fileWatcher";
	private static final String ENABLED_NAME = "enabled";
	private static final String CHANGE_SETTLING_DELAY_NAME = "changeSettlingDelayMilliseconds";
	private static final String MISSING_DIRECTORY_RETRY_INTERVAL_NAME = "missingDirectoryRetryIntervalMilliseconds";
	private static final DelayedTaskScheduler NO_SAVE_SCHEDULER = (command, delay) -> CompletableFuture.completedFuture(null);

	public ConfigFileWatcherSettings {
		changeSettlingDelay = requirePositiveDuration(
			changeSettlingDelay,
			"changeSettlingDelay"
		);
		missingDirectoryRetryInterval = requirePositiveDuration(
			missingDirectoryRetryInterval,
			"missingDirectoryRetryInterval"
		);
	}

	public static ConfigFileWatcherSettings defaults() {
		return new ConfigFileWatcherSettings(
			true,
			FileWatcher.DEFAULT_CHANGE_SETTLING_DELAY,
			FileWatcher.DEFAULT_MISSING_DIRECTORY_RETRY_INTERVAL
		);
	}

	public static ConfigFileWatcherSettings load(Path configRootDir) {
		configRootDir = ErrorUtil.checkNotNull(configRootDir, "configRootDir");
		SchemaData schemaData = createSchema(configRootDir, NO_SAVE_SCHEDULER);
		schemaData.schema()
			.loadIfNeeded();
		return schemaData.settings();
	}

	public static ConfigSchema registerSchema(ConfigManager configManager, Path configRootDir) {
		configManager = ErrorUtil.checkNotNull(configManager, "configManager");
		configRootDir = ErrorUtil.checkNotNull(configRootDir, "configRootDir");
		SchemaData schemaData = createSchema(configRootDir, configManager.getSaveScheduler());
		ConfigSchema schema = schemaData.schema();
		configManager.registerSchema(schema);
		return schema;
	}

	private static SchemaData createSchema(Path configRootDir, DelayedTaskScheduler scheduler) {
		scheduler = ErrorUtil.checkNotNull(scheduler, "scheduler");
		ConfigCategoryBuilder category = new ConfigCategoryBuilder(LOCALIZATION_PATH, CATEGORY_NAME);
		ConfigValue<Boolean> enabled = category.addBoolean(ENABLED_NAME, true)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		ConfigValue<Long> changeSettlingDelayMilliseconds = category.addLong(
				CHANGE_SETTLING_DELAY_NAME,
				FileWatcher.DEFAULT_CHANGE_SETTLING_DELAY.toMillis(),
				1L,
				Long.MAX_VALUE
			)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		ConfigValue<Long> missingDirectoryRetryIntervalMilliseconds = category.addLong(
				MISSING_DIRECTORY_RETRY_INTERVAL_NAME,
				FileWatcher.DEFAULT_MISSING_DIRECTORY_RETRY_INTERVAL.toMillis(),
				1L,
				Long.MAX_VALUE
			)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		ConfigSchema schema = new ConfigSchema(
			getConfigFile(configRootDir),
			List.of(category),
			scheduler
		);
		return new SchemaData(
			schema,
			enabled,
			changeSettlingDelayMilliseconds,
			missingDirectoryRetryIntervalMilliseconds
		);
	}

	private static Path getConfigFile(Path configRootDir) {
		return configRootDir.resolve(CONFIG_DIRECTORY_NAME)
			.resolve(CONFIG_FILE_NAME);
	}

	private static Duration requirePositiveDuration(Duration duration, String name) {
		duration = ErrorUtil.checkNotNull(duration, name);
		if (duration.isNegative() || duration.isZero()) {
			throw new IllegalArgumentException(name + " must be positive.");
		}
		return duration;
	}

	private record SchemaData(
		ConfigSchema schema,
		ConfigValue<Boolean> enabled,
		ConfigValue<Long> changeSettlingDelayMilliseconds,
		ConfigValue<Long> missingDirectoryRetryIntervalMilliseconds
	) {
		private ConfigFileWatcherSettings settings() {
			return new ConfigFileWatcherSettings(
				enabled.getValue(),
				Duration.ofMillis(changeSettlingDelayMilliseconds.getValue()),
				Duration.ofMillis(missingDirectoryRetryIntervalMilliseconds.getValue())
			);
		}
	}
}
