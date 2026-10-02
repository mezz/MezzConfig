package net.mezzdev.config.server;

import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.editor.ConfigValueRestartRequirement;
import net.mezzdev.config.file.ConfigFileWatcherSettings;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigSchemaDefinition;
import net.mezzdev.config.schema.ConfigSchemaBuilder;
import net.mezzdev.config.schema.ConfigSchemaPathResolver;
import net.mezzdev.config.schema.LayeredConfigSchemaPathResolver;
import net.mezzdev.config.value.ConfigValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerConfigRuntimeTest {
	@Test
	public void serverSynchronizationIsOnlyAvailableAfterRegistration() {
		ConfigManager manager = createConfigManager();
		TestStringConfig config = createStringServerConfig(
			new ServerConfigKey("unregistered_test", "server.ini"), Optional::empty, 1, "default"
		);
		List<ServerConfigValueData> snapshot = List.of(new ServerConfigValueData("general", "value_0", "\"remote\""));
		AtomicInteger notifications = new AtomicInteger();
		config.values().getFirst().addListener(ignored -> notifications.incrementAndGet());

		assertTrue(manager.getServerSynchronization(config.definition().getServerKey()).isEmpty());
		assertEquals("default", config.values().getFirst().get());
		assertEquals(0, notifications.get());

		ConfigSchema schema = manager.registerSchema(config.definition());
		schema.getServerSynchronization().orElseThrow().applyRemoteSnapshot(snapshot);
		assertTrue(schema.isActive());
		assertEquals("remote", config.values().getFirst().get());
		assertEquals(1, notifications.get());
	}

	@Test
	public void initialLoadCompletesBeforeServerSynchronizationIsAvailable(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("server.ini");
		Files.writeString(path, "[general]\nvalue_0 = loaded\n");
		ServerConfigKey key = new ServerConfigKey("initial_load_test", "server.ini");
		ConfigManager manager = createConfigManager();
		TestStringConfig config = createStringServerConfig(key, () -> Optional.of(path), 1, "default");
		AtomicInteger notifications = new AtomicInteger();
		RecordingServer server = new RecordingServer(tempDir, key);
		Runnable removeListener = config.values().getFirst().addListener(change -> {
			notifications.incrementAndGet();
			assertTrue(manager.getServerSynchronization(key).isEmpty());
			if (change.newValue().equals("loaded")) {
				config.values().getFirst().set("initialized");
			}
			assertTrue(server.snapshots.isEmpty());
		});

		ServerConfigRuntime.onServerStarted(server);
		try {
			manager.registerSchema(config.definition());
			removeListener.run();
			assertEquals(2, notifications.get());
			ConfigSchema.ServerSynchronization synchronization = manager.getServerSynchronization(key).orElseThrow();
			assertEquals(List.of(new ServerConfigValueData("general", "value_0", "initialized")), synchronization.serializeValues());
			assertEquals(List.of(new ServerConfigSyncPayload(key, synchronization.serializeValues())), server.snapshots);

			config.values().getFirst().set("edited");
			assertEquals(List.of("initialized", "edited"), server.snapshots.stream()
				.map(snapshot -> snapshot.values().getFirst().serializedValue())
				.toList());
		} finally {
			ServerConfigRuntime.onServerStopped();
		}
	}

	@Test
	public void failedPublicationDoesNotExposeSynchronizationAndAllowsRetry(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("server.ini");
		Files.writeString(path, "[general]\nvalue_0 = loaded\n");
		ServerConfigKey key = new ServerConfigKey("publication_test", "server.ini");
		TestStringConfig config = createStringServerConfig(key, () -> Optional.of(path), 1, "default");
		IllegalStateException failure = new IllegalStateException("Publication failed");

		assertSame(failure, assertThrows(IllegalStateException.class, () -> config.definition().initialize(null, false, paths -> {}, initializedSchema -> {
			assertEquals("loaded", config.values().getFirst().get());
			throw failure;
		})));
		assertEquals("default", config.values().getFirst().get());

		ConfigManager manager = createConfigManager();
		manager.registerSchema(config.definition());
		ConfigSchema.ServerSynchronization synchronization = manager.getServerSynchronization(key).orElseThrow();
		assertEquals(List.of(new ServerConfigValueData("general", "value_0", "loaded")), synchronization.serializeValues());
	}

	@Test
	public void clientLifecycleInvalidatesAllSchemasBeforeNotifyingListeners(@TempDir Path tempDir) throws IOException {
		ConfigManager manager = createConfigManager();
		ServerConfigClientConnection connection = new ServerConfigClientConnection(() -> manager);
		Path fallback = Files.createDirectories(tempDir.resolve("fallback"));
		Path identified = Files.createDirectories(tempDir.resolve("identified"));
		List<IConfigValue<Integer>> values = new ArrayList<>();
		List<ConfigSchema> schemas = new ArrayList<>();
		for (String file : List.of("first.ini", "second.ini")) {
			Files.writeString(fallback.resolve(file), "[general]\nvalue = 11\n");
			Files.writeString(identified.resolve(file), "[general]\nvalue = 22\n");
			ConfigSchemaPathResolver resolver = new LayeredConfigSchemaPathResolver(
				tempDir.resolve("default").resolve(file),
				() -> {
					if (!connection.isWorldActive()) {
						return Optional.empty();
					}
					Path directory = fallback;
					if (connection.getRemoteServerId().isPresent()) {
						directory = identified;
					}
					return Optional.of(directory.resolve(file));
				}
			);
			ConfigSchemaBuilder builder = new ConfigSchemaBuilder(
				"lifecycle_test", resolver, "lifecycle_test.config", manager, ConfigSchemaType.CLIENT_PER_WORLD, null
			);
			values.add(builder.addCategory("general").addInteger("value", 0).build());
			schemas.add(builder.build());
		}
		List<Integer> otherSchemaValues = new ArrayList<>();
		values.getFirst().addListener(ignored -> otherSchemaValues.add(values.getLast().get()));

		connection.onWorldStarted();
		assertEquals(11, values.getFirst().get());
		connection.handleServerIdentity(new ServerIdentityPayload(UUID.randomUUID()));
		assertEquals(22, values.getFirst().get());
		connection.onDisconnect();

		assertEquals(List.of(11, 22, 0), otherSchemaValues);
		assertEquals(0, values.getFirst().get());
		assertTrue(schemas.stream().noneMatch(ConfigSchema::isActive));
		assertTrue(schemas.stream().allMatch(schema -> schema.getPath().isEmpty()));
	}

	@Test
	public void serverLifecycleFlushesTheOldWorldAndLoadsTheNext(@TempDir Path tempDir) throws IOException {
		ConfigManager manager = createConfigManager();
		AtomicReference<Optional<Path>> activePath = new AtomicReference<>(Optional.empty());
		TestStringConfig config = createStringServerConfig(
			new ServerConfigKey("server_lifecycle_test", "server.ini"), activePath::get, 1, "default"
		);
		ConfigSchema schema = manager.registerSchema(config.definition());
		Path first = tempDir.resolve("first.ini");
		Path second = tempDir.resolve("second.ini");
		Files.writeString(first, "[general]\nvalue_0 = first\n");
		Files.writeString(second, "[general]\nvalue_0 = second\n");

		activePath.set(Optional.of(first));
		manager.onWorldStarted();
		assertEquals("first", config.values().getFirst().get());
		config.values().getFirst().set("pending-first");
		activePath.set(Optional.empty());
		manager.onServerStopped();
		assertFalse(schema.isActive());
		assertEquals("default", config.values().getFirst().get());
		assertTrue(Files.readString(first).contains("pending-first"));

		activePath.set(Optional.of(second));
		manager.onWorldStarted();
		assertEquals("second", config.values().getFirst().get());
	}

	@Test
	public void failedLocalPathActivationKeepsRemoteSnapshot(@TempDir Path tempDir) {
		ConfigManager manager = createConfigManager();
		AtomicReference<Optional<Path>> activePath = new AtomicReference<>(Optional.empty());
		TestStringConfig config = createStringServerConfig(
			new ServerConfigKey("path_collision_test", "server.ini"), activePath::get, 1, "default"
		);
		ConfigSchema schema = manager.registerSchema(config.definition());
		schema.getServerSynchronization().orElseThrow().applyRemoteSnapshot(List.of(new ServerConfigValueData("general", "value_0", "\"remote\"")));
		Path occupied = tempDir.resolve("occupied.ini");
		manager.createSortingConfig(occupied, Comparator.naturalOrder(), true);

		activePath.set(Optional.of(occupied));
		assertThrows(IllegalArgumentException.class, manager::onWorldStarted);

		activePath.set(Optional.empty());
		assertEquals("remote", config.values().getFirst().get());
		assertTrue(schema.isActive());
	}

	@Test
	public void remoteClientConnectionAppliesChunkedSnapshotAndClearsItOnDisconnect(@TempDir Path tempDir) {
		// Setup: a server has a large authoritative value while a remote client starts with its local default.
		ServerConfigKey key = new ServerConfigKey("remote_flow_test", "server.ini");
		String authoritativeValue = "server-value-" + "x".repeat(ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH);
		TestStringConfig source = createStringServerConfig(
			key,
			() -> Optional.of(tempDir.resolve("server.ini")),
			1,
			authoritativeValue
		);
		ConfigManager serverManager = createConfigManager();
		ConfigSchema sourceSchema = serverManager.registerSchema(source.definition());

		ConfigManager clientManager = createConfigManager();
		TestStringConfig target = createStringServerConfig(key, Optional::empty, 1, "client-default");
		ConfigSchema targetSchema = clientManager.registerSchema(target.definition());
		ServerConfigClientConnection connection = new ServerConfigClientConnection(() -> clientManager);
		UUID serverId = UUID.randomUUID();

		// Operation: establish server identity and transmit the authoritative snapshot in chunks.
		connection.handleServerIdentity(new ServerIdentityPayload(serverId));
		int chunkCount = transmit(connection, new ServerConfigSyncPayload(key, sourceSchema.serializeValues()));

		// Assertions: the remote schema becomes active, pathless, authoritative, and read-only.
		assertEquals(Optional.of(serverId), connection.getRemoteServerId());
		assertTrue(chunkCount > 1);
		assertTrue(targetSchema.isActive());
		assertEquals(Optional.empty(), targetSchema.getPath());
		assertEquals(authoritativeValue, target.values().getFirst().get());
		assertThrows(IllegalStateException.class, () -> target.values().getFirst().set("client-edit"));

		// Operation: disconnect the remote client.
		connection.onDisconnect();

		// Assertions: remote identity and synchronized state are cleared back to local defaults.
		assertEquals(Optional.empty(), connection.getRemoteServerId());
		assertFalse(targetSchema.isActive());
		assertEquals("client-default", target.values().getFirst().get());
	}

	@Test
	public void integratedServerConnectionKeepsTheLocalAuthoritativeSnapshot(@TempDir Path tempDir) {
		// Setup: an integrated client shares a manager with its local authoritative server schema.
		ServerConfigKey key = new ServerConfigKey("integrated_flow_test", "server.ini");
		ConfigManager integratedManager = createConfigManager();
		TestStringConfig authoritative = createStringServerConfig(
			key,
			() -> Optional.of(tempDir.resolve("server.ini")),
			1,
			"local-authoritative"
		);
		ConfigSchema schema = integratedManager.registerSchema(authoritative.definition());
		ServerConfigClientConnection connection = new ServerConfigClientConnection(() -> integratedManager);

		// Operation: receive identity and a loopback snapshot containing a conflicting remote value.
		connection.handleServerIdentity(new ServerIdentityPayload(UUID.randomUUID()));
		transmit(connection, new ServerConfigSyncPayload(
			key,
			List.of(new ServerConfigValueData("general", "value_0", "\"remote-loopback\""))
		));

		// Assertions: the active file-backed schema keeps its local authoritative state.
		assertTrue(schema.isActive());
		assertEquals(Optional.of(tempDir.resolve("server.ini")), schema.getPath());
		assertEquals("local-authoritative", authoritative.values().getFirst().get());

		// Operation: disconnect the integrated client connection.
		connection.onDisconnect();

		// Assertions: disconnect does not deactivate or reset the shared local server schema.
		assertTrue(schema.isActive());
		assertEquals("local-authoritative", authoritative.values().getFirst().get());
	}

	@Test
	public void registrationEnforcesServerSnapshotValueCount() {
		// Setup: one server schema reaches the synchronization value limit and another exceeds it by one.
		ConfigManager manager = createConfigManager();
		ConfigSchemaDefinition maximumDefinition = createStringServerConfig(
				new ServerConfigKey("maximum_values", "server.ini"),
				() -> Optional.empty(),
				ServerConfigPayloadCodec.MAX_VALUE_COUNT,
				"value"
			)
			.definition();
		ConfigSchemaDefinition excessiveDefinition = createStringServerConfig(
				new ServerConfigKey("excessive_values", "server.ini"),
				() -> Optional.empty(),
				ServerConfigPayloadCodec.MAX_VALUE_COUNT + 1,
				"value"
			)
			.definition();

		// Operation: register the boundary schema, then attempt the excessive schema.
		ConfigSchema schema = manager.registerSchema(maximumDefinition);
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> manager.registerSchema(excessiveDefinition)
		);

		// Assertions: the excessive schema is rejected with context and never published.
		assertTrue(exception.getMessage().contains("cannot be synchronized"));
		assertTrue(exception.getMessage().contains("Too many server config values"));
		assertEquals(List.of(schema), List.copyOf(manager.getSchemas()));
	}

	@Test
	public void registrationEnforcesSerializedValueLength() {
		// Setup: one schema reaches the per-value synchronization limit and another exceeds it.
		ConfigManager manager = createConfigManager();
		ConfigSchemaDefinition maximumDefinition = createStringServerConfig(
				new ServerConfigKey("maximum_value", "server.ini"),
				() -> Optional.empty(),
				1,
				"x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES)
			)
			.definition();
		ConfigSchemaDefinition excessiveDefinition = createStringServerConfig(
				new ServerConfigKey("excessive_value", "server.ini"),
				() -> Optional.empty(),
				1,
				"x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES + 1)
			)
			.definition();

		// Operation: register the boundary schema, then attempt the excessive value.
		ConfigSchema schema = manager.registerSchema(maximumDefinition);
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> manager.registerSchema(excessiveDefinition)
		);

		// Assertions: the per-value limit reports its cause and leaves only the valid schema registered.
		assertTrue(exception.getMessage().contains("serialized value"));
		assertEquals(List.of(schema), List.copyOf(manager.getSchemas()));
	}

	@Test
	public void registrationEnforcesTotalSnapshotLength() {
		// Setup: one schema stays within the aggregate synchronization limit and another exceeds it.
		ConfigManager manager = createConfigManager();
		ConfigSchemaDefinition largeDefinition = createStringServerConfig(
				new ServerConfigKey("large_snapshot", "server.ini"),
				() -> Optional.empty(),
				4,
				"x".repeat(220 * 1024)
			)
			.definition();
		ConfigSchemaDefinition excessiveDefinition = createStringServerConfig(
				new ServerConfigKey("excessive_snapshot", "server.ini"),
				() -> Optional.empty(),
				5,
				"x".repeat(220 * 1024)
			)
			.definition();

		// Operation: register the valid schema, then attempt the excessive snapshot.
		ConfigSchema schema = manager.registerSchema(largeDefinition);
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> manager.registerSchema(excessiveDefinition)
		);

		// Assertions: the aggregate limit reports its cause and leaves only the valid schema registered.
		assertTrue(exception.getMessage().contains("maximum length"));
		assertEquals(List.of(schema), List.copyOf(manager.getSchemas()));
	}

	@Test
	public void oversizedAuthoritativeUpdateIsRejectedBeforeMutation(@TempDir Path tempDir) {
		// Setup: an active server schema has one small authoritative and pending value.
		TestStringConfig config = createStringServerConfig(
			new ServerConfigKey("update_test", "server.ini"),
			() -> Optional.of(tempDir.resolve("server.ini")),
			1,
			"original"
		);
		ConfigSchema schema = config.definition().initialize(null, false);

		// Operation: try to replace it with a value beyond the synchronization limit.
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> schema.batchUpdate(updater -> updater.set(
				config.values().getFirst(),
				"x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES + 1)
			))
		);

		// Assertions: validation reports synchronization failure before either view mutates.
		assertTrue(exception.getMessage().contains("cannot be synchronized"));
		assertEquals("original", config.values().getFirst().get());
		assertEquals("original", config.values().getFirst().getEditorInfo().getPendingValue());
	}

	@Test
	public void updateRejectsSnapshotThatWouldBeOversizedAfterRestart(@TempDir Path tempDir) {
		// Setup: restart-gated server values currently form a valid snapshot but their queued replacements would not.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		List<ConfigValue<String>> values = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			values.add(builder.addString("value_" + i, "x".repeat(80 * 1024))
				.setRestartRequirement(ConfigValueRestartRequirement.WORLD_RESTART)
				.build());
		}
		ConfigSchemaDefinition definition = new ConfigSchemaDefinition(
			"server.ini",
			"restart_test",
			() -> Optional.of(tempDir.resolve("server.ini")),
			List.of(builder),
			List.of(builder),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigSchemaType.SERVER,
			new ServerConfigKey("restart_test", "server.ini"),
			null
		);
		ConfigSchema schema = definition.initialize(null, false);
		String prospectiveValue = "x".repeat(220 * 1024);

		// Operation: queue oversized replacements for every restart-gated value in one batch.
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> schema.batchUpdate(updater -> values.forEach(value -> updater.set(value, prospectiveValue)))
		);

		// Assertions: prospective snapshot validation leaves both effective and pending values unchanged.
		assertTrue(exception.getMessage().contains("maximum length"));
		assertTrue(values.stream().allMatch(value -> value.get().length() == 80 * 1024));
		assertTrue(values.stream().allMatch(value -> value.getEditorInfo().getPendingValue().length() == 80 * 1024));
	}

	@Test
	public void oversizedServerFileReloadKeepsPreviousSnapshot(@TempDir Path tempDir) throws IOException {
		// Setup: an active server schema later resolves to a file with an oversized serialized value.
		Path originalPath = tempDir.resolve("original.ini");
		Path oversizedPath = tempDir.resolve("oversized.ini");
		AtomicReference<Optional<Path>> activePath = new AtomicReference<>(Optional.of(originalPath));
		TestStringConfig config = createStringServerConfig(
			new ServerConfigKey("reload_test", "server.ini"),
			activePath::get,
			1,
			"original"
		);
		ConfigSchema schema = config.definition().initialize(null, false);
		AtomicInteger notifications = new AtomicInteger();
		schema.addBatchListener(ignored -> notifications.incrementAndGet());
		Files.writeString(
			oversizedPath,
			"[general]\nvalue_0 = " + "x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES + 1) + "\n"
		);

		// Operation: switch the active resolver to the oversized file.
		activePath.set(Optional.of(oversizedPath));
		schema.invalidatePaths();

		// Assertions: the path changes, but the prior snapshot remains and listeners are not notified.
		assertEquals(Optional.of(oversizedPath), schema.getPath());
		assertEquals("original", config.values().getFirst().get());
		assertEquals(0, notifications.get());
	}

	@Test
	public void registrationRejectsOversizedInitialFileWithoutPublishing(@TempDir Path tempDir) throws IOException {
		// Setup: a server schema definition points to an initial file with an oversized value.
		Path path = tempDir.resolve("server.ini");
		String oversizedValue = "x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES + 1);
		Files.writeString(path, "[general]\nvalue_0 = " + oversizedValue + "\n");
		TestStringConfig config = createStringServerConfig(
			new ServerConfigKey("initial_file_test", "server.ini"),
			() -> Optional.of(path),
			1,
			"original"
		);
		ConfigManager manager = createConfigManager();
		AtomicInteger notifications = new AtomicInteger();
		config.values().getFirst().addListener(ignored -> notifications.incrementAndGet());

		// Operation: try to register and initialize the schema from that file.
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> manager.registerSchema(config.definition())
		);

		// Assertions: registration publishes nothing, retains defaults, and leaves the source file untouched.
		assertTrue(exception.getMessage().contains("cannot be synchronized"));
		assertEquals(List.of(), List.copyOf(manager.getSchemas()));
		assertEquals("original", config.values().getFirst().getEffectiveValueWithoutLoading());
		assertEquals(0, notifications.get());
		assertTrue(Files.readString(path).contains(oversizedValue));

		Files.writeString(path, "[general]\nvalue_0 = corrected\n");
		ConfigSchema schema = manager.registerSchema(config.definition());
		assertEquals("corrected", config.values().getFirst().get());
		assertEquals(1, notifications.get());
		ConfigSchema.ServerSynchronization synchronization = schema.getServerSynchronization().orElseThrow();
		assertEquals(List.of(new ServerConfigValueData("general", "value_0", "corrected")), synchronization.serializeValues());
	}

	private static TestStringConfig createStringServerConfig(
		ServerConfigKey key,
		ConfigSchemaPathResolver pathResolver,
		int valueCount,
		String defaultValue
	) {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		List<ConfigValue<String>> values = new ArrayList<>(valueCount);
		for (int i = 0; i < valueCount; i++) {
			values.add(builder.addString("value_" + i, defaultValue)
				.build());
		}
		ConfigSchemaDefinition definition = new ConfigSchemaDefinition(
			key.configFileName(),
			key.modId(),
			pathResolver,
			List.of(builder),
			List.of(builder),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigSchemaType.SERVER,
			key,
			null
		);
		return new TestStringConfig(definition, List.copyOf(values));
	}

	private static ConfigManager createConfigManager() {
		return new ConfigManager(
			"Server Snapshot Validation Test",
			ConfigFileWatcherSettings.clientDefaults().withEnabled(false),
			ConfigFileWatcherSettings.serverDefaults().withEnabled(false)
		);
	}

	private static int transmit(ServerConfigClientConnection connection, ServerConfigSyncPayload payload) {
		List<byte[]> chunks = ServerConfigPayloadChunker.split(ServerConfigPayloadCodec.encodeSync(payload));
		chunks.stream()
			.map(ServerConfigSyncChunkPayload::new)
			.forEach(connection::handleSyncChunk);
		return chunks.size();
	}

	private static final class RecordingServer implements ServerConfigRuntime.Server, ServerConfigRuntime.Player {
		private final Path worldRoot;
		private final ServerConfigKey key;
		private final ServerConfigPayloadReassembler receiver = new ServerConfigPayloadReassembler();
		private final List<ServerConfigSyncPayload> snapshots = new ArrayList<>();

		private RecordingServer(Path worldRoot, ServerConfigKey key) {
			this.worldRoot = worldRoot;
			this.key = key;
		}

		@Override
		public Path getWorldRoot() {
			return worldRoot;
		}

		@Override
		public void execute(Runnable task) {
			task.run();
		}

		@Override
		public Iterable<? extends ServerConfigRuntime.Player> getPlayers() {
			return List.of(this);
		}

		@Override
		public String getName() {
			return "Test player";
		}

		@Override
		public boolean sendIdentity(UUID serverId) {
			return true;
		}

		@Override
		public boolean sendSync(byte[] data) {
			receiver.accept(data)
				.map(ServerConfigPayloadCodec::decodeSync)
				.filter(snapshot -> snapshot.key().equals(key))
				.ifPresent(snapshots::add);
			return true;
		}
	}

	private record TestStringConfig(ConfigSchemaDefinition definition, List<ConfigValue<String>> values) {}
}
