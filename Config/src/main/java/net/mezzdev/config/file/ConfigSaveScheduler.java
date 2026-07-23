package net.mezzdev.config.file;

import java.time.Duration;
import java.util.concurrent.Future;

/**
 * Schedules config saves for a schema.
 */
@FunctionalInterface
public interface ConfigSaveScheduler {
	Future<?> schedule(Runnable command, Duration delay);
}
