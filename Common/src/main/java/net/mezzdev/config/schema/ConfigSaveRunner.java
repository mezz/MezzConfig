package net.mezzdev.config.schema;

import net.mezzdev.deduplicatingrunner.DeduplicatingRunner;
import net.mezzdev.deduplicatingrunner.DelayedTaskScheduler;

import java.time.Duration;

/**
 * Runs delayed config saves, replacing any queued save with the latest one.
 */
final class ConfigSaveRunner {
	private final DeduplicatingRunner runner;

	ConfigSaveRunner(Duration delay, DelayedTaskScheduler scheduler) {
		this.runner = new DeduplicatingRunner(delay, scheduler);
	}

	public void run(Runnable runnable) {
		runner.run(runnable);
	}
}
