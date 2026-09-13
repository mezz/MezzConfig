package net.mezzdev.config.server;

import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.value.editor.ConfigValueRestartRequirement;
import net.mezzdev.config.file.ConfigFileWatcherSettings;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigSchemaPathResolver;
import net.mezzdev.config.value.ConfigValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerConfigRuntimeTest {
	@Test
	public void remoteClientConnectionAppliesChunkedSnapshotAndClearsItOnDisconnect(@TempDir Path tempDir) {
		// Setup: a server has a large authoritative value while a remote client starts with its local default.
		ServerConfigKey key = new ServerConfigKey("remote_flow_test", "server.ini");
		String authoritativeValue = "server-value-" + "x".repeat(ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH);
		TestStringSchema source = createStringServerSchema(
			key,
			() -> Optional.of(tempDir.resolve("server.ini")),
			1,
			authoritativeValue
		);
		ConfigManager serverManager = createConfigManager();
		serverManager.registerSchema(source.schema());

		ConfigManager clientManager = createConfigManager();
		TestStringSchema target = createStringServerSchema(key, Optional::empty, 1, "client-default");
		clientManager.registerSchema(target.schema());
		ServerConfigClientConnection connection = new ServerConfigClientConnection(() -> clientManager);
		UUID serverId = UUID.randomUUID();

		// Operation: establish server identity and transmit the authoritative snapshot in chunks.
		connection.handleServerIdentity(new ServerIdentityPayload(serverId));
		int chunkCount = transmit(connection, new ServerConfigSyncPayload(key, source.schema().serializeValues()));

		// Assertions: the remote schema becomes active, pathless, authoritative, and read-only.
		assertEquals(Optional.of(serverId), connection.getRemoteServerId());
		assertTrue(chunkCount > 1);
		assertTrue(target.schema().isActive());
		assertEquals(Optional.empty(), target.schema().getPath());
		assertEquals(authoritativeValue, target.values().getFirst().get());
		assertThrows(IllegalStateException.class, () -> target.values().getFirst().set("client-edit"));

		// Operation: disconnect the remote client.
		connection.onDisconnect();

		// Assertions: remote identity and synchronized state are cleared back to local defaults.
		assertEquals(Optional.empty(), connection.getRemoteServerId());
		assertFalse(target.schema().isActive());
		assertEquals("client-default", target.values().getFirst().get());
	}

	@Test
	public void integratedServerConnectionKeepsTheLocalAuthoritativeSnapshot(@TempDir Path tempDir) {
		// Setup: an integrated client shares a manager with its local authoritative server schema.
		ServerConfigKey key = new ServerConfigKey("integrated_flow_test", "server.ini");
		ConfigManager integratedManager = createConfigManager();
		TestStringSchema authoritative = createStringServerSchema(
			key,
			() -> Optional.of(tempDir.resolve("server.ini")),
			1,
			"local-authoritative"
		);
		integratedManager.registerSchema(authoritative.schema());
		ServerConfigClientConnection connection = new ServerConfigClientConnection(() -> integratedManager);

		// Operation: receive identity and a loopback snapshot containing a conflicting remote value.
		connection.handleServerIdentity(new ServerIdentityPayload(UUID.randomUUID()));
		transmit(connection, new ServerConfigSyncPayload(
			key,
			List.of(new ServerConfigValueData("general", "value_0", "\"remote-loopback\""))
		));

		// Assertions: the active file-backed schema keeps its local authoritative state.
		assertTrue(authoritative.schema().isActive());
		assertEquals(Optional.of(tempDir.resolve("server.ini")), authoritative.schema().getPath());
		assertEquals("local-authoritative", authoritative.values().getFirst().get());

		// Operation: disconnect the integrated client connection.
		connection.onDisconnect();

		// Assertions: disconnect does not deactivate or reset the shared local server schema.
		assertTrue(authoritative.schema().isActive());
		assertEquals("local-authoritative", authoritative.values().getFirst().get());
	}

	@Test
	public void registrationEnforcesServerSnapshotValueCount() {
		// Setup: one server schema reaches the synchronization value limit and another exceeds it by one.
		ConfigManager manager = createConfigManager();
		ConfigSchema maximum = createStringServerSchema(
				new ServerConfigKey("maximum_values", "server.ini"),
				() -> Optional.empty(),
				ServerConfigPayloadCodec.MAX_VALUE_COUNT,
				"value"
			)
			.schema();
		ConfigSchema excessive = createStringServerSchema(
				new ServerConfigKey("excessive_values", "server.ini"),
				() -> Optional.empty(),
				ServerConfigPayloadCodec.MAX_VALUE_COUNT + 1,
				"value"
			)
			.schema();

		// Operation: register the boundary schema, then attempt the excessive schema.
		manager.registerSchema(maximum);
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> manager.registerSchema(excessive)
		);

		// Assertions: the excessive schema is rejected with context and never published.
		assertTrue(exception.getMessage().contains("cannot be synchronized"));
		assertTrue(exception.getMessage().contains("Too many server config values"));
		assertEquals(List.of(maximum), List.copyOf(manager.getSchemas()));
	}

	@Test
	public void registrationEnforcesSerializedValueLength() {
		// Setup: one schema reaches the per-value synchronization limit and another exceeds it.
		ConfigManager manager = createConfigManager();
		ConfigSchema maximumValue = createStringServerSchema(
				new ServerConfigKey("maximum_value", "server.ini"),
				() -> Optional.empty(),
				1,
				"x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES)
			)
			.schema();
		ConfigSchema excessiveValue = createStringServerSchema(
				new ServerConfigKey("excessive_value", "server.ini"),
				() -> Optional.empty(),
				1,
				"x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES + 1)
			)
			.schema();

		// Operation: register the boundary schema, then attempt the excessive value.
		manager.registerSchema(maximumValue);
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> manager.registerSchema(excessiveValue)
		);

		// Assertions: the per-value limit reports its cause and leaves only the valid schema registered.
		assertTrue(exception.getMessage().contains("serialized value"));
		assertEquals(List.of(maximumValue), List.copyOf(manager.getSchemas()));
	}

	@Test
	public void registrationEnforcesTotalSnapshotLength() {
		// Setup: one schema stays within the aggregate synchronization limit and another exceeds it.
		ConfigManager manager = createConfigManager();
		ConfigSchema largeSnapshot = createStringServerSchema(
				new ServerConfigKey("large_snapshot", "server.ini"),
				() -> Optional.empty(),
				4,
				"x".repeat(220 * 1024)
			)
			.schema();
		ConfigSchema excessiveSnapshot = createStringServerSchema(
				new ServerConfigKey("excessive_snapshot", "server.ini"),
				() -> Optional.empty(),
				5,
				"x".repeat(220 * 1024)
			)
			.schema();

		// Operation: register the valid schema, then attempt the excessive snapshot.
		manager.registerSchema(largeSnapshot);
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> manager.registerSchema(excessiveSnapshot)
		);

		// Assertions: the aggregate limit reports its cause and leaves only the valid schema registered.
		assertTrue(exception.getMessage().contains("maximum length"));
		assertEquals(List.of(largeSnapshot), List.copyOf(manager.getSchemas()));
	}

	@Test
	public void oversizedAuthoritativeUpdateIsRejectedBeforeMutation(@TempDir Path tempDir) {
		// Setup: an active server schema has one small authoritative and pending value.
		TestStringSchema testSchema = createStringServerSchema(
			new ServerConfigKey("update_test", "server.ini"),
			() -> Optional.of(tempDir.resolve("server.ini")),
			1,
			"original"
		);
		testSchema.schema().register(null, false);

		// Operation: try to replace it with a value beyond the synchronization limit.
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> testSchema.schema().batchUpdate(updater -> updater.set(
				testSchema.values().getFirst(),
				"x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES + 1)
			))
		);

		// Assertions: validation reports synchronization failure before either view mutates.
		assertTrue(exception.getMessage().contains("cannot be synchronized"));
		assertEquals("original", testSchema.values().getFirst().get());
		assertEquals("original", testSchema.values().getFirst().getEditorInfo().getPendingValue());
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
		ConfigSchema schema = new ConfigSchema(
			"restart_test",
			() -> Optional.of(tempDir.resolve("server.ini")),
			List.of(builder),
			List.of(builder),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigSchemaType.SERVER,
			new ServerConfigKey("restart_test", "server.ini")
		);
		schema.register(null, false);
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
		TestStringSchema testSchema = createStringServerSchema(
			new ServerConfigKey("reload_test", "server.ini"),
			activePath::get,
			1,
			"original"
		);
		testSchema.schema().register(null, false);
		AtomicInteger notifications = new AtomicInteger();
		testSchema.schema().addBatchListener(ignored -> notifications.incrementAndGet());
		Files.writeString(
			oversizedPath,
			"[general]\nvalue_0 = " + "x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES + 1) + "\n"
		);

		// Operation: switch the active resolver to the oversized file.
		activePath.set(Optional.of(oversizedPath));

		// Assertions: the path changes, but the prior snapshot remains and listeners are not notified.
		assertEquals(Optional.of(oversizedPath), testSchema.schema().getPath());
		assertEquals("original", testSchema.values().getFirst().get());
		assertEquals(0, notifications.get());
	}

	@Test
	public void registrationRejectsOversizedInitialFileWithoutPublishing(@TempDir Path tempDir) throws IOException {
		// Setup: an unregistered server schema points to an initial file with an oversized value.
		Path path = tempDir.resolve("server.ini");
		String oversizedValue = "x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES + 1);
		Files.writeString(path, "[general]\nvalue_0 = " + oversizedValue + "\n");
		TestStringSchema testSchema = createStringServerSchema(
			new ServerConfigKey("initial_file_test", "server.ini"),
			() -> Optional.of(path),
			1,
			"original"
		);
		ConfigManager manager = createConfigManager();

		// Operation: try to register and initialize the schema from that file.
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> manager.registerSchema(testSchema.schema())
		);

		// Assertions: registration publishes nothing, retains defaults, and leaves the source file untouched.
		assertTrue(exception.getMessage().contains("cannot be synchronized"));
		assertEquals(List.of(), List.copyOf(manager.getSchemas()));
		assertEquals("original", testSchema.values().getFirst().getEffectiveValueWithoutLoading());
		assertTrue(Files.readString(path).contains(oversizedValue));
	}

	private static TestStringSchema createStringServerSchema(
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
		ConfigSchema schema = new ConfigSchema(
			key.modId(),
			pathResolver,
			List.of(builder),
			List.of(builder),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigSchemaType.SERVER,
			key
		);
		return new TestStringSchema(schema, List.copyOf(values));
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

	private record TestStringSchema(ConfigSchema schema, List<ConfigValue<String>> values) {}
}
