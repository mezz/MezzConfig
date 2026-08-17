package net.mezzdev.config.file;

import net.mezzdev.config.api.schema.ConfigOwnership;
import net.mezzdev.config.api.schema.ConfigScope;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigSchemaPathResolver;
import net.mezzdev.config.server.ServerConfigKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigManagerTest {
	@Test
	public void schemasRegisteredAfterManagerSnapshotRemainVisible() {
		ConfigManager manager = new ConfigManager(
			"Disabled Test File Watcher",
			new ConfigFileWatcherSettings(false, Duration.ofMillis(1), Duration.ofMillis(1))
		);
		List<?> beforeRegistration = List.copyOf(manager.getSchemas());
		ConfigSchema schema = createServerSchema(
			new ServerConfigKey("late_test_mod", "server.ini"),
			() -> Optional.empty()
		);

		manager.registerSchema(schema);

		assertEquals(List.of(), beforeRegistration);
		assertEquals(List.of(schema), List.copyOf(manager.getSchemas()));
	}

	@Test
	public void duplicateServerSchemaDoesNotInitializeOrReplaceOriginal() {
		ConfigManager manager = new ConfigManager(
			"Disabled Test File Watcher",
			new ConfigFileWatcherSettings(false, Duration.ofMillis(1), Duration.ofMillis(1))
		);
		ServerConfigKey key = new ServerConfigKey("test_mod", "server.ini");
		ConfigSchema original = createServerSchema(key, () -> Optional.empty());
		AtomicInteger duplicatePathResolutions = new AtomicInteger();
		ConfigSchema duplicate = createServerSchema(key, () -> {
			duplicatePathResolutions.incrementAndGet();
			return Optional.empty();
		});

		manager.registerSchema(original);
		assertThrows(IllegalArgumentException.class, () -> manager.registerSchema(duplicate));

		assertEquals(0, duplicatePathResolutions.get());
		assertSame(original, manager.getServerSchema(key).orElseThrow());
		assertEquals(List.of(original), List.copyOf(manager.getSchemas()));
	}

	@Test
	public void failedRegistrationCanRetryWithoutRetainedState(@TempDir Path tempDir) throws IOException {
		ConfigManager manager = new ConfigManager(
			"Disabled Test File Watcher",
			new ConfigFileWatcherSettings(false, Duration.ofMillis(1), Duration.ofMillis(1))
		);
		Path path = tempDir.resolve("client.ini");
		Files.createDirectory(path);
		ConfigSchema schema = createInstallationSchema(path);

		assertThrows(UncheckedIOException.class, () -> manager.registerSchema(schema));
		assertEquals(List.of(), List.copyOf(manager.getSchemas()));

		Files.delete(path);
		manager.registerSchema(schema);

		assertEquals(List.of(schema), List.copyOf(manager.getSchemas()));
		assertTrue(Files.isRegularFile(path));
	}

	private static ConfigSchema createServerSchema(ServerConfigKey key, ConfigSchemaPathResolver pathResolver) {
		ConfigCategoryBuilder category = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		category.addBoolean("enabled", true)
			.build();
		return new ConfigSchema(
			key.modId(),
			pathResolver,
			List.of(category),
			List.of(category),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigOwnership.SERVER,
			ConfigScope.WORLD,
			key
		);
	}

	private static ConfigSchema createInstallationSchema(Path path) {
		ConfigCategoryBuilder category = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		category.addBoolean("enabled", true)
			.build();
		return new ConfigSchema(
			"test_mod",
			path,
			List.of(category),
			(command, delay) -> CompletableFuture.completedFuture(null)
		);
	}
}
