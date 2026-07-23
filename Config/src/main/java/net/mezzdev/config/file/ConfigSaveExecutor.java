package net.mezzdev.config.file;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Schedules delayed config saves and flushes pending saves during shutdown.
 */
final class ConfigSaveExecutor implements ConfigSaveScheduler {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

	private final ScheduledExecutorService service;
	private final Set<ScheduledTask> scheduledTasks = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean shutdown = new AtomicBoolean(false);

	ConfigSaveExecutor(String threadName) {
		this.service = createDefaultService(threadName);
		Runtime.getRuntime()
			.addShutdownHook(new Thread(this::shutdown, threadName + " Shutdown"));
	}

	private static ScheduledThreadPoolExecutor createDefaultService(String threadName) {
		var threadFactory = new ThreadFactoryBuilder()
			.setNameFormat(threadName + " %d")
			.setDaemon(true)
			.build();
		var service = new ScheduledThreadPoolExecutor(
			1,
			threadFactory
		);
		service.setRemoveOnCancelPolicy(true);
		service.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
		return service;
	}

	@Override
	public Future<?> schedule(Runnable command, Duration delay) {
		if (isShutdown()) {
			return runImmediately(command);
		}
		ScheduledTask scheduledTask = new ScheduledTask(command);
		scheduledTasks.add(scheduledTask);
		try {
			Future<?> future = service.schedule(scheduledTask, delay.toMillis(), TimeUnit.MILLISECONDS);
			scheduledTask.setFuture(future);
			return new TrackedFuture<>(future, scheduledTask);
		} catch (RejectedExecutionException e) {
			scheduledTasks.remove(scheduledTask);
			if (isShutdown()) {
				return runImmediately(command);
			}
			throw e;
		} catch (RuntimeException e) {
			scheduledTasks.remove(scheduledTask);
			throw e;
		}
	}

	private static Future<?> runImmediately(Runnable command) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		try {
			command.run();
			future.complete(null);
		} catch (RuntimeException | LinkageError e) {
			future.completeExceptionally(e);
		}
		return future;
	}

	void shutdown() {
		if (!shutdown.compareAndSet(false, true)) {
			return;
		}
		runScheduledTasksImmediately();
		service.shutdown();
		try {
			if (!service.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
				forceShutdown("Timed out waiting for delayed config saves to finish.");
			}
		} catch (InterruptedException ignored) {
			forceShutdown("Interrupted while waiting for delayed config saves to finish.");
			Thread.currentThread().interrupt();
		}
	}

	private boolean isShutdown() {
		return shutdown.get() || service.isShutdown();
	}

	private void runScheduledTasksImmediately() {
		for (ScheduledTask scheduledTask : scheduledTasks) {
			scheduledTasks.remove(scheduledTask);
			if (scheduledTask.cancel()) {
				try {
					service.execute(() -> runScheduledTaskDuringShutdown(scheduledTask));
				} catch (RejectedExecutionException e) {
					runScheduledTaskDuringShutdown(scheduledTask);
				}
			}
		}
	}

	private void runScheduledTaskDuringShutdown(ScheduledTask scheduledTask) {
		try {
			scheduledTask.command().run();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("Failed to execute delayed config save during shutdown.", e);
		}
	}

	private void forceShutdown(String message) {
		List<Runnable> droppedTasks = service.shutdownNow();
		if (droppedTasks.isEmpty()) {
			LOGGER.error("{} Forcing shutdown.", message);
		} else {
			LOGGER.error("{} Forcing shutdown. {} delayed config saves never started.", message, droppedTasks.size());
		}
	}

	private final class TrackedFuture<T> implements Future<T> {
		private final Future<T> delegate;
		private final ScheduledTask scheduledTask;

		private TrackedFuture(Future<T> delegate, ScheduledTask scheduledTask) {
			this.delegate = delegate;
			this.scheduledTask = scheduledTask;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			boolean canceled = delegate.cancel(mayInterruptIfRunning);
			if (canceled) {
				scheduledTasks.remove(scheduledTask);
			}
			return canceled;
		}

		@Override
		public boolean isCancelled() {
			return delegate.isCancelled();
		}

		@Override
		public boolean isDone() {
			return delegate.isDone();
		}

		@Override
		public T get() throws InterruptedException, ExecutionException {
			return delegate.get();
		}

		@Override
		public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			return delegate.get(timeout, unit);
		}
	}

	private final class ScheduledTask implements Runnable {
		private final Runnable command;
		private @Nullable volatile Future<?> future;

		private ScheduledTask(Runnable command) {
			this.command = command;
		}

		@Override
		public void run() {
			try {
				command.run();
			} finally {
				scheduledTasks.remove(this);
			}
		}

		public Runnable command() {
			return command;
		}

		public void setFuture(Future<?> future) {
			this.future = future;
		}

		public boolean cancel() {
			Future<?> future = this.future;
			if (future == null) {
				return true;
			}
			future.cancel(false);
			return future.isCancelled();
		}
	}
}
