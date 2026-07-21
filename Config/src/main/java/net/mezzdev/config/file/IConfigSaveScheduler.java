package net.mezzdev.config.file;

import java.time.Duration;
import java.util.concurrent.Future;

@FunctionalInterface
public interface IConfigSaveScheduler {
	Future<?> schedule(Runnable command, Duration delay);
}
