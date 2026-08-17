package net.mezzdev.config.file;

import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.filewatcher.FileWatcher;

import java.time.Duration;

public record ConfigFileWatcherSettings(
	boolean enabled,
	Duration changeSettlingDelay,
	Duration missingDirectoryRetryInterval
) {
	public static final Duration DEFAULT_CLIENT_CHANGE_SETTLING_DELAY = FileWatcher.DEFAULT_CHANGE_SETTLING_DELAY;
	public static final Duration DEFAULT_SERVER_CHANGE_SETTLING_DELAY = Duration.ofSeconds(2);
	public static final Duration DEFAULT_CLIENT_MISSING_DIRECTORY_RETRY_INTERVAL = FileWatcher.DEFAULT_MISSING_DIRECTORY_RETRY_INTERVAL;
	public static final Duration DEFAULT_SERVER_MISSING_DIRECTORY_RETRY_INTERVAL = FileWatcher.DEFAULT_MISSING_DIRECTORY_RETRY_INTERVAL;

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

	public static ConfigFileWatcherSettings clientDefaults() {
		return new ConfigFileWatcherSettings(
			true,
			DEFAULT_CLIENT_CHANGE_SETTLING_DELAY,
			DEFAULT_CLIENT_MISSING_DIRECTORY_RETRY_INTERVAL
		);
	}

	public static ConfigFileWatcherSettings serverDefaults() {
		return new ConfigFileWatcherSettings(
			true,
			DEFAULT_SERVER_CHANGE_SETTLING_DELAY,
			DEFAULT_SERVER_MISSING_DIRECTORY_RETRY_INTERVAL
		);
	}

	public ConfigFileWatcherSettings withEnabled(boolean enabled) {
		return new ConfigFileWatcherSettings(
			enabled,
			changeSettlingDelay,
			missingDirectoryRetryInterval
		);
	}

	private static Duration requirePositiveDuration(Duration duration, String name) {
		duration = ErrorUtil.checkNotNull(duration, name);
		if (duration.isNegative() || duration.isZero()) {
			throw new IllegalArgumentException(name + " must be positive.");
		}
		return duration;
	}
}
