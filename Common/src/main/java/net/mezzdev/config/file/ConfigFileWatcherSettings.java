package net.mezzdev.config.file;

import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.filewatcher.FileWatcher;

import java.time.Duration;

public record ConfigFileWatcherSettings(
	boolean enabled,
	Duration changeSettlingDelay,
	Duration missingDirectoryRetryInterval
) {
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

	private static Duration requirePositiveDuration(Duration duration, String name) {
		duration = ErrorUtil.checkNotNull(duration, name);
		if (duration.isNegative() || duration.isZero()) {
			throw new IllegalArgumentException(name + " must be positive.");
		}
		return duration;
	}
}
