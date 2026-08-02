package net.mezzdev.config.file;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigSaveExecutorTest {
	@Test
	public void scheduleAfterShutdownRunsImmediately() {
		ConfigSaveExecutor executor = new ConfigSaveExecutor("Mezz Config Test Save Scheduler");
		executor.shutdown();

		AtomicBoolean ran = new AtomicBoolean(false);
		Future<?> future = executor.schedule(() -> ran.set(true), Duration.ZERO);

		assertTrue(future.isDone());
		assertTrue(ran.get());
	}

	@Test
	public void scheduleDuringShutdownRunsImmediately() {
		ConfigSaveExecutor executor = new ConfigSaveExecutor("Mezz Config Test Save Scheduler");
		AtomicInteger runCount = new AtomicInteger();
		executor.schedule(
			() -> {
				runCount.incrementAndGet();
				executor.schedule(runCount::incrementAndGet, Duration.ZERO);
			},
			Duration.ofSeconds(1)
		);

		executor.shutdown();

		assertEquals(2, runCount.get());
	}
}
