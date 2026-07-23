package net.mezzdev.config.files;

import java.time.Duration;
import java.util.concurrent.Future;

/**
 * Schedules config file saves.
 *
 * @since 19.39.0
 */
public interface IConfigSaveScheduler {
	/**
	 * Schedule a config file save after a delay.
	 *
	 * @since 19.39.0
	 */
	Future<?> schedule(Runnable command, Duration delay);
}
