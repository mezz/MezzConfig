package net.mezzdev.config.file;

import net.mezzdev.deduplicatingrunner.DelayedExecutor;
import net.mezzdev.deduplicatingrunner.DelayedTaskScheduler;

import java.time.Duration;
import java.util.concurrent.Future;

/**
 * Schedules delayed config saves and flushes pending saves during shutdown.
 */
final class ConfigSaveExecutor implements DelayedTaskScheduler {
	private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

	private final DelayedExecutor executor;

	ConfigSaveExecutor(String threadName) {
		this.executor = new DelayedExecutor(SHUTDOWN_TIMEOUT, threadName);
		Runtime.getRuntime()
			.addShutdownHook(new Thread(this::shutdown, threadName + " Shutdown"));
	}

	@Override
	public Future<?> schedule(Runnable command, Duration delay) {
		return executor.schedule(command, delay);
	}

	void shutdown() {
		executor.shutdown();
	}
}
