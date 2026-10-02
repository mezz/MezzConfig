package net.mezzdev.config.file;

import net.mezzdev.config.schema.StaticConfigSchemaPathResolver;
import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.value.serializer.IDeserializeResult;
import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigSchemaDefinition;
import net.mezzdev.config.schema.ConfigSchemaPathResolver;
import net.mezzdev.config.server.ServerConfigKey;
import net.mezzdev.config.serializers.BooleanSerializer;
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
		// Operation: create the default watcher settings for client-owned and server-owned files.
		ConfigFileWatcherSettings clientSettings = ConfigFileWatcherSettings.clientDefaults();
		ConfigFileWatcherSettings serverSettings = ConfigFileWatcherSettings.serverDefaults();

		// Assertions: each side uses its declared retry timing, with extra settling time on the server.
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
		// Setup: watching is enabled only for server schemas before client and server schemas are registered.
		ConfigManager manager = new ConfigManager(
			"Ownership Test File Watcher",
			ConfigFileWatcherSettings.clientDefaults().withEnabled(false),
			new ConfigFileWatcherSettings(true, Duration.ofMillis(25), Duration.ofSeconds(1))
		);
		Path clientPath = tempDir.resolve("client.ini");
		Path serverPath = tempDir.resolve("server.ini");
		TestConfig client = createClientConfig(clientPath);
		TestConfig server = createServerConfig(serverPath);
		manager.startWatching();
		manager.registerSchema(client.definition());
		manager.registerSchema(server.definition());
		AtomicReference<Thread> reloadListenerThread = new AtomicReference<>();
		server.enabled().addListener(ignored -> reloadListenerThread.set(Thread.currentThread()));

		// Operation: modify both files and wait for the server-owned value to reload.
		Files.writeString(clientPath, "[general]\nenabled = false\n");
		Files.writeString(serverPath, "[general]\nenabled = false\n");

		Thread readingThread = Thread.currentThread();
		awaitValue(server.enabled(), false);

		// Assertions: only the server schema reloads, and its listener runs on the reading thread.
		assertTrue(client.enabled().get());
		assertFalse(server.enabled().get());
		assertSame(readingThread, reloadListenerThread.get());
	}

	@Test
	public void schemasRegisteredAfterManagerSnapshotRemainVisible() {
		// Setup: a caller snapshots the manager's initially empty schema collection.
		ConfigManager manager = createDisabledConfigManager();
		List<?> beforeRegistration = List.copyOf(manager.getSchemas());
		ConfigSchemaDefinition definition = createServerDefinition(
			new ServerConfigKey("late_test_mod", "server.ini"),
			() -> Optional.empty()
		);

		// Operation: register a schema after the earlier snapshot was taken.
		ConfigSchema schema = manager.registerSchema(definition);

		// Assertions: the old snapshot stays immutable while a fresh view contains the schema.
		assertEquals(List.of(), beforeRegistration);
		assertEquals(List.of(schema), List.copyOf(manager.getSchemas()));
	}

	@Test
	public void duplicateServerSchemaDoesNotInitializeOrReplaceOriginal() {
		// Setup: two server schemas share a key, and resolving the duplicate path has an observable side effect.
		ConfigManager manager = createDisabledConfigManager();
		ServerConfigKey key = new ServerConfigKey("test_mod", "server.ini");
		ConfigSchemaDefinition originalDefinition = createServerDefinition(key, () -> Optional.empty());
		AtomicInteger duplicatePathResolutions = new AtomicInteger();
		ConfigSchemaDefinition duplicateDefinition = createServerDefinition(key, () -> {
			duplicatePathResolutions.incrementAndGet();
			return Optional.empty();
		});

		// Operation: register the original and then attempt to register its duplicate.
		ConfigSchema schema = manager.registerSchema(originalDefinition);
		assertThrows(IllegalArgumentException.class, () -> manager.registerSchema(duplicateDefinition));

		// Assertions: the duplicate is rejected before initialization and the original remains registered.
		assertEquals(0, duplicatePathResolutions.get());
		assertSame(schema, manager.getServerSynchronization(key).orElseThrow().getSchema());
		assertEquals(List.of(schema), List.copyOf(manager.getSchemas()));
	}

	@Test
	public void failedRegistrationCanRetryWithoutRetainedState(@TempDir Path tempDir) throws IOException {
		// Setup: schema initialization points at a directory, forcing its first registration to fail.
		ConfigManager manager = createDisabledConfigManager();
		Path path = tempDir.resolve("client.ini");
		Files.createDirectory(path);
		ConfigSchemaDefinition definition = createClientDefinition(path);

		// Operation: attempt registration while the path is invalid.
		assertThrows(UncheckedIOException.class, () -> manager.registerSchema(definition));

		// Assertions: failed registration publishes no schema state.
		assertEquals(List.of(), List.copyOf(manager.getSchemas()));

		// Operation: remove the obstruction and retry the same schema.
		Files.delete(path);
		ConfigSchema schema = manager.registerSchema(definition);

		// Assertions: retry succeeds and initializes the file normally.
		assertEquals(List.of(schema), List.copyOf(manager.getSchemas()));
		assertTrue(Files.isRegularFile(path));
	}

	@Test
	public void duplicateSortingConfigPathsAreRejectedBeforeFileAccess(@TempDir Path tempDir) {
		// Setup: one file-backed sorting config already reserves a path without writing it yet.
		ConfigManager manager = createDisabledConfigManager();
		Path path = tempDir.resolve("sorting.ini");
		manager.createSortingConfig(path, Comparator.naturalOrder(), true);

		// Operation: request another sorting config for the same path.
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> manager.createSortingConfig(path, Comparator.naturalOrder(), false)
		);

		// Assertions: the error identifies the absolute path and no file access occurs.
		assertTrue(exception.getMessage().contains(path.toAbsolutePath().toString()));
		assertFalse(Files.exists(path));
	}

	@Test
	public void sortingReservationRejectsSchemaBeforeFileCreation(@TempDir Path tempDir) {
		// Setup: a sorting config reserves the path later requested by a schema.
		ConfigManager manager = createDisabledConfigManager();
		Path path = tempDir.resolve("shared.ini");
		manager.createSortingConfig(path, Comparator.naturalOrder(), true);
		ConfigSchemaDefinition definition = createClientDefinition(path);

		// Operation: try to register the colliding schema.
		assertThrows(IllegalArgumentException.class, () -> manager.registerSchema(definition));

		// Assertions: collision detection precedes file creation and schema publication.
		assertFalse(Files.exists(path));
		assertTrue(manager.getSchemas().isEmpty());
	}

	@Test
	public void schemaReservationRejectsSortingWithoutModifyingFile(@TempDir Path tempDir) throws IOException {
		// Setup: an initialized schema reserves a path and writes its default file.
		ConfigManager manager = createDisabledConfigManager();
		Path path = tempDir.resolve("shared.ini");
		ConfigSchemaDefinition definition = createClientDefinition(path);
		manager.registerSchema(definition);
		String originalContents = Files.readString(path);

		// Operation: try to create a sorting config at the schema's path.
		assertThrows(
			IllegalArgumentException.class,
			() -> manager.createSortingConfig(path, Comparator.naturalOrder(), true)
		);

		// Assertions: the collision leaves the schema file unchanged.
		assertEquals(originalContents, Files.readString(path));
	}

	@Test
	public void schemaPathIdentityIsNormalizedAndAbsolute(@TempDir Path tempDir) throws IOException {
		// Setup: client and server schemas reference the same file through syntactically different paths.
		ConfigManager manager = createDisabledConfigManager();
		Path path = tempDir.resolve("shared.ini");
		Path equivalentPath = tempDir.resolve("unused").resolve("..").resolve("shared.ini");
		ConfigSchemaDefinition clientDefinition = createClientDefinition(equivalentPath);
		ConfigSchemaDefinition serverDefinition = createServerConfig(path).definition();
		ConfigSchema clientSchema = manager.registerSchema(clientDefinition);
		String originalContents = Files.readString(path);

		// Operation: register the server schema after the equivalent client path is reserved.
		assertThrows(IllegalArgumentException.class, () -> manager.registerSchema(serverDefinition));

		// Assertions: normalized absolute identity catches the collision without modifying or publishing it.
		assertEquals(originalContents, Files.readString(path));
		assertEquals(List.of(clientSchema), List.copyOf(manager.getSchemas()));
	}

	@Test
	public void unchangedWorldContextKeepsUnsavedEdits(@TempDir Path tempDir) throws IOException {
		ConfigManager manager = createDisabledConfigManager();
		Path path = tempDir.resolve("client.ini");
		TestConfig config = createClientConfig(path);
		manager.registerSchema(config.definition());
		assertTrue(config.enabled().set(false));
		assertTrue(Files.readString(path).contains("enabled = true"));

		manager.onWorldStarted();
		assertFalse(config.enabled().get());
		assertFalse(config.enabled().getPendingValue());
	}

	@Test
	public void valuesKeepDefaultsUntilInitialLoadingFinishes(@TempDir Path tempDir) throws IOException {
		ConfigManager manager = createDisabledConfigManager();
		Path path = tempDir.resolve("initializing.ini");
		Files.writeString(path, "[general]\nenabled = false\nobserve = true\n");
		ConfigCategoryBuilder category = new ConfigCategoryBuilder("test.config", "general");
		ConfigValue<Boolean> enabled = category.addBoolean("enabled", true).build();
		AtomicReference<List<Boolean>> valuesReadDuringLoading = new AtomicReference<>();
		IConfigValueSerializer<Boolean> observer = new IConfigValueSerializer<>() {
			@Override
			public String serialize(Boolean value) {
				return BooleanSerializer.INSTANCE.serialize(value);
			}

			@Override
			public IDeserializeResult<Boolean> deserialize(String text) {
				valuesReadDuringLoading.set(List.of(enabled.get(), enabled.getPendingValue()));
				return BooleanSerializer.INSTANCE.deserialize(text);
			}

			@Override
			public boolean isValid(Boolean value) {
				return BooleanSerializer.INSTANCE.isValid(value);
			}

			@Override
			public String getValidValuesDescription() {
				return BooleanSerializer.INSTANCE.getValidValuesDescription();
			}
		};
		category.addValue("observe", false, observer).build();
		ConfigSchemaDefinition definition = new ConfigSchemaDefinition(
			"test.ini",
			"mezz_config",
			new StaticConfigSchemaPathResolver(path),
			List.of(category),
			List.of(category),
			manager.getSaveScheduler(),
			ConfigSchemaType.CLIENT,
			null,
			null
		);
		valuesReadDuringLoading.set(null);

		ConfigSchema schema = manager.registerSchema(definition);

		assertEquals(List.of(true, true), valuesReadDuringLoading.get());
		assertFalse(enabled.get());
		assertFalse(enabled.getPendingValue());
		assertEquals(Optional.of(path), schema.getPath());
	}

	@Test
	public void readsBeforeRegistrationKeepDefaultsUntilTheFileIsReserved(@TempDir Path tempDir) throws IOException {
		ConfigManager manager = createDisabledConfigManager();
		Path path = tempDir.resolve("shared.ini");
		Files.writeString(path, "[general]\nenabled = false\n");
		ConfigCategoryBuilder category = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = category.addBoolean("enabled", true).build();
		ConfigSchemaDefinition definition = new ConfigSchemaDefinition(
			"test.ini",
			"mezz_config",
			new StaticConfigSchemaPathResolver(path),
			List.of(category),
			List.of(category),
			manager.getSaveScheduler(),
			ConfigSchemaType.CLIENT,
			null,
			null
		);
		assertTrue(enabled.get());
		assertTrue(enabled.getPendingValue());
		assertThrows(IllegalStateException.class, () -> enabled.set(false));

		manager.registerSchema(definition);
		assertThrows(IllegalArgumentException.class, () -> manager.createSortingConfig(path, Comparator.naturalOrder(), true));
		assertFalse(enabled.get());
		assertFalse(enabled.getPendingValue());
	}

	@Test
	public void dynamicSchemaPathCollisionIsRejectedBeforeActivation(@TempDir Path tempDir) {
		// Setup: an inactive world schema later resolves to a path already reserved by a sorting config.
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
		ConfigSchemaDefinition definition = createClientWorldDefinition(pathResolver);
		manager.registerSchema(definition);

		activePath.set(Optional.of(tempDir.resolve("unused").resolve("..").resolve("sorting.ini")));

		assertThrows(IllegalArgumentException.class, manager::onWorldStarted);

		// Assertions: activation stops before the reserved sorting file is created.
		assertFalse(Files.exists(sortingPath));
	}

	@Test
	public void failedPathTransitionKeepsOwnershipAndCanRetry(@TempDir Path tempDir) {
		ConfigManager manager = createDisabledConfigManager();
		Path first = tempDir.resolve("first.ini");
		Path occupied = tempDir.resolve("occupied.ini");
		Path next = tempDir.resolve("next.ini");
		AtomicReference<Optional<Path>> activePath = new AtomicReference<>(Optional.of(first));
		ConfigSchemaDefinition definition = createServerDefinition(new ServerConfigKey("path_test", "server.ini"), activePath::get);
		ConfigSchema schema = manager.registerSchema(definition);
		manager.createSortingConfig(occupied, Comparator.naturalOrder(), true);

		activePath.set(Optional.of(occupied));
		assertThrows(IllegalArgumentException.class, manager::onWorldStarted);
		assertFalse(Files.exists(occupied));
		assertThrows(IllegalArgumentException.class, () -> manager.createSortingConfig(first, Comparator.naturalOrder(), true));

		// The failed load should retry on the next read without another call to onWorldStarted().
		activePath.set(Optional.of(next));
		assertEquals(Optional.of(next), schema.getPath());
		assertThrows(IllegalArgumentException.class, () -> manager.createSortingConfig(next, Comparator.naturalOrder(), true));
		manager.createSortingConfig(first, Comparator.naturalOrder(), true);
	}

	@Test
	public void inMemorySortingConfigsDoNotReserveFilePaths(@TempDir Path tempDir) {
		// Setup: a path is available for a file-backed sorting config.
		ConfigManager manager = createDisabledConfigManager();
		Path path = tempDir.resolve("sorting.ini");

		// Operation: create unrelated in-memory configs before reserving the file-backed path.
		manager.createInMemorySortingConfig(Comparator.naturalOrder(), true);
		manager.createInMemorySortingConfig(Comparator.reverseOrder(), false);
		manager.createSortingConfig(path, Comparator.naturalOrder(), true);

		// Assertions: in-memory configs cause no collision or eager file creation.
		assertFalse(Files.exists(path));
	}

	private static ConfigSchemaDefinition createServerDefinition(ServerConfigKey key, ConfigSchemaPathResolver pathResolver) {
		ConfigCategoryBuilder category = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		category.addBoolean("enabled", true)
			.build();
		return new ConfigSchemaDefinition(
			key.configFileName(),
			key.modId(),
			pathResolver,
			List.of(category),
			List.of(category),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigSchemaType.SERVER,
			key,
			null
		);
	}

	private static ConfigSchemaDefinition createClientWorldDefinition(ConfigSchemaPathResolver pathResolver) {
		ConfigCategoryBuilder category = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		category.addBoolean("enabled", true)
			.build();
		return new ConfigSchemaDefinition(
			"test.ini",
			"client_world_test_mod",
			pathResolver,
			List.of(category),
			List.of(category),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigSchemaType.CLIENT_PER_WORLD,
			null,
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

	private static ConfigSchemaDefinition createClientDefinition(Path path) {
		return createClientConfig(path).definition();
	}

	private static TestConfig createClientConfig(Path path) {
		ConfigCategoryBuilder category = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = category.addBoolean("enabled", true)
			.build();
		ConfigSchemaDefinition definition = new ConfigSchemaDefinition(
			"test.ini",
			"test_mod",
			() -> Optional.of(path),
			List.of(category),
			List.of(category),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigSchemaType.CLIENT,
			null,
			null
		);
		return new TestConfig(definition, enabled);
	}

	private static TestConfig createServerConfig(Path path) {
		ConfigCategoryBuilder category = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = category.addBoolean("enabled", true)
			.build();
		ServerConfigKey key = new ServerConfigKey("test_mod", path.getFileName().toString());
		ConfigSchemaDefinition definition = new ConfigSchemaDefinition(
			key.configFileName(),
			key.modId(),
			() -> Optional.of(path),
			List.of(category),
			List.of(category),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigSchemaType.SERVER,
			key,
			null
		);
		return new TestConfig(definition, enabled);
	}

	private static <T> void awaitValue(ConfigValue<T> value, T expected) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline) {
			if (expected.equals(value.get())) {
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

	private record TestConfig(ConfigSchemaDefinition definition, ConfigValue<Boolean> enabled) {}
}
