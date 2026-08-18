package net.mezzdev.config.file;

import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigSchemaPathResolver;
import net.mezzdev.config.server.ServerConfigKey;
import net.mezzdev.config.value.ConfigValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigManagerTest {
	@Test
	public void clientAndServerDefaultsUseTheirExpectedChangeSettlingDelays() {
		ConfigFileWatcherSettings clientSettings = ConfigFileWatcherSettings.clientDefaults();
		ConfigFileWatcherSettings serverSettings = ConfigFileWatcherSettings.serverDefaults();

		assertEquals(ConfigFileWatcherSettings.DEFAULT_CLIENT_CHANGE_SETTLING_DELAY, clientSettings.changeSettlingDelay());
		assertEquals(ConfigFileWatcherSettings.DEFAULT_SERVER_CHANGE_SETTLING_DELAY, serverSettings.changeSettlingDelay());
		assertEquals(
			ConfigFileWatcherSettings.DEFAULT_CLIENT_MISSING_DIRECTORY_RETRY_INTERVAL,
			clientSettings.missingDirectoryRetryInterval()
		);
		assertEquals(
			ConfigFileWatcherSettings.DEFAULT_SERVER_MISSING_DIRECTORY_RETRY_INTERVAL,
			serverSettings.missingDirectoryRetryInterval()
		);
		assertTrue(serverSettings.changeSettlingDelay().compareTo(clientSettings.changeSettlingDelay()) > 0);
	}

	@Test
	public void schemasRegisteredAfterWatchingStartsUseTheirOwnershipSettings(@TempDir Path tempDir) throws IOException {
		ConfigManager manager = new ConfigManager(
			"Ownership Test File Watcher",
			ConfigFileWatcherSettings.clientDefaults().withEnabled(false),
			new ConfigFileWatcherSettings(true, Duration.ofMillis(25), Duration.ofSeconds(1))
		);
		Path clientPath = tempDir.resolve("client.ini");
		Path serverPath = tempDir.resolve("server.ini");
		InstallationSchema client = createClientInstallationSchema(clientPath);
		InstallationSchema server = createStaticServerSchema(serverPath);
		manager.startWatching();
		manager.registerSchema(client.schema());
		manager.registerSchema(server.schema());
		AtomicReference<Thread> reloadListenerThread = new AtomicReference<>();
		server.enabled().addListener(ignored -> reloadListenerThread.set(Thread.currentThread()));

		Files.writeString(clientPath, "[general]\nenabled = false\n");
		Files.writeString(serverPath, "[general]\nenabled = false\n");

		Thread readingThread = Thread.currentThread();
		awaitValue(server.enabled(), false);
		assertTrue(client.enabled().getValue());
		assertFalse(server.enabled().getValue());
		assertSame(readingThread, reloadListenerThread.get());
	}

	@Test
	public void schemasRegisteredAfterManagerSnapshotRemainVisible() {
		ConfigManager manager = createDisabledConfigManager();
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
		ConfigManager manager = createDisabledConfigManager();
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
		ConfigManager manager = createDisabledConfigManager();
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

	@Test
	public void duplicateSortingConfigPathsAreRejectedBeforeFileAccess(@TempDir Path tempDir) {
		ConfigManager manager = createDisabledConfigManager();
		Path path = tempDir.resolve("sorting.ini");
		manager.createSortingConfig(path, Comparator.naturalOrder(), true);

		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> manager.createSortingConfig(path, Comparator.naturalOrder(), false)
		);

		assertTrue(exception.getMessage().contains(path.toAbsolutePath().toString()));
		assertFalse(Files.exists(path));
	}

	@Test
	public void sortingReservationRejectsSchemaBeforeFileCreation(@TempDir Path tempDir) {
		ConfigManager manager = createDisabledConfigManager();
		Path path = tempDir.resolve("shared.ini");
		manager.createSortingConfig(path, Comparator.naturalOrder(), true);
		ConfigSchema schema = createInstallationSchema(path);

		assertThrows(IllegalArgumentException.class, () -> manager.registerSchema(schema));

		assertFalse(Files.exists(path));
		assertFalse(manager.getSchemas().contains(schema));
	}

	@Test
	public void schemaReservationRejectsSortingWithoutModifyingFile(@TempDir Path tempDir) throws IOException {
		ConfigManager manager = createDisabledConfigManager();
		Path path = tempDir.resolve("shared.ini");
		ConfigSchema schema = createInstallationSchema(path);
		manager.registerSchema(schema);
		String originalContents = Files.readString(path);

		assertThrows(
			IllegalArgumentException.class,
			() -> manager.createSortingConfig(path, Comparator.naturalOrder(), true)
		);

		assertEquals(originalContents, Files.readString(path));
	}

	@Test
	public void schemaPathIdentityIsNormalizedAndAbsolute(@TempDir Path tempDir) throws IOException {
		ConfigManager manager = createDisabledConfigManager();
		Path path = tempDir.resolve("shared.ini");
		Path equivalentPath = tempDir.resolve("unused").resolve("..").resolve("shared.ini");
		ConfigSchema clientSchema = createInstallationSchema(equivalentPath);
		ConfigSchema serverSchema = createStaticServerSchema(path).schema();
		manager.registerSchema(clientSchema);
		String originalContents = Files.readString(path);

		assertThrows(IllegalArgumentException.class, () -> manager.registerSchema(serverSchema));

		assertEquals(originalContents, Files.readString(path));
		assertFalse(manager.getSchemas().contains(serverSchema));
	}

	@Test
	public void dynamicSchemaPathCollisionIsRejectedBeforeActivation(@TempDir Path tempDir) {
		ConfigManager manager = createDisabledConfigManager();
		Path sortingPath = tempDir.resolve("sorting.ini");
		manager.createSortingConfig(sortingPath, Comparator.naturalOrder(), true);
		AtomicReference<Optional<Path>> activePath = new AtomicReference<>(Optional.empty());
		ConfigSchemaPathResolver pathResolver = new ConfigSchemaPathResolver() {
			@Override
			public Optional<Path> resolvePath() {
				return activePath.get();
			}

			@Override
			public Optional<Path> resolveDefaultPath() {
				return Optional.of(tempDir.resolve("world/default.ini"));
			}
		};
		ConfigSchema schema = createClientWorldSchema(pathResolver);
		manager.registerSchema(schema);

		activePath.set(Optional.of(tempDir.resolve("unused").resolve("..").resolve("sorting.ini")));

		assertThrows(IllegalArgumentException.class, schema::getPath);
		assertFalse(Files.exists(sortingPath));
	}

	@Test
	public void inMemorySortingConfigsDoNotReserveFilePaths(@TempDir Path tempDir) {
		ConfigManager manager = createDisabledConfigManager();
		Path path = tempDir.resolve("sorting.ini");

		manager.createInMemorySortingConfig(Comparator.naturalOrder(), true);
		manager.createInMemorySortingConfig(Comparator.reverseOrder(), false);
		manager.createSortingConfig(path, Comparator.naturalOrder(), true);

		assertFalse(Files.exists(path));
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
			ConfigSchemaType.SERVER,
			key
		);
	}

	private static ConfigSchema createClientWorldSchema(ConfigSchemaPathResolver pathResolver) {
		ConfigCategoryBuilder category = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		category.addBoolean("enabled", true)
			.build();
		return new ConfigSchema(
			"client_world_test_mod",
			pathResolver,
			List.of(category),
			List.of(category),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigSchemaType.CLIENT_PER_WORLD,
			null
		);
	}

	private static ConfigManager createDisabledConfigManager() {
		return new ConfigManager(
			"Disabled Test File Watcher",
			ConfigFileWatcherSettings.clientDefaults().withEnabled(false),
			ConfigFileWatcherSettings.serverDefaults().withEnabled(false)
		);
	}

	private static ConfigSchema createInstallationSchema(Path path) {
		return createClientInstallationSchema(path).schema();
	}

	private static InstallationSchema createClientInstallationSchema(Path path) {
		ConfigCategoryBuilder category = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = category.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = new ConfigSchema(
			"test_mod",
			() -> Optional.of(path),
			List.of(category),
			List.of(category),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigSchemaType.CLIENT,
			null
		);
		return new InstallationSchema(schema, enabled);
	}

	private static InstallationSchema createStaticServerSchema(Path path) {
		ConfigCategoryBuilder category = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = category.addBoolean("enabled", true)
			.build();
		ServerConfigKey key = new ServerConfigKey("test_mod", path.getFileName().toString());
		ConfigSchema schema = new ConfigSchema(
			key.modId(),
			() -> Optional.of(path),
			List.of(category),
			List.of(category),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigSchemaType.SERVER,
			key
		);
		return new InstallationSchema(schema, enabled);
	}

	private static <T> void awaitValue(ConfigValue<T> value, T expected) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline) {
			if (expected.equals(value.getValue())) {
				return;
			}
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Interrupted while waiting for config reload.", e);
			}
		}
		throw new AssertionError("Config value was not reloaded with: " + expected);
	}

	private record InstallationSchema(ConfigSchema schema, ConfigValue<Boolean> enabled) {}
}
