package net.mezzdev.config.file;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.Future;

/**
 * Runs delayed config saves, replacing any queued save with the latest one.
 */
final class ConfigSaveRunner {
	private final ConfigSaveScheduler scheduler;
	private final Duration delay;
	private @Nullable Future<?> future;

	ConfigSaveRunner(Duration delay, ConfigSaveScheduler scheduler) {
		this.delay = delay;
		this.scheduler = scheduler;
	}

	public synchronized void run(Runnable runnable) {
		if (future != null) {
			future.cancel(false);
		}
		future = scheduler.schedule(runnable, delay);
	}
}
