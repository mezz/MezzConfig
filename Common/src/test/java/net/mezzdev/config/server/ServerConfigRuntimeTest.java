package net.mezzdev.config.server;

import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.mezzdev.config.file.ConfigFileWatcherSettings;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigSchemaPathResolver;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.value.ConfigValueUpdate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerConfigRuntimeTest {
	@AfterEach
	public void cleanUpClientState() {
		ServerConfigRuntime.onClientDisconnect();
		ServerConfigNetworking.setClientSender(payload -> false);
	}

	@Test
	public void pendingRemoteRequestTimesOutExactlyOnce() {
		TestSchema testSchema = createRemoteServerSchema();
		ServerConfigNetworking.setClientSender(payload -> true);
		CompletableFuture<Void> future = ServerConfigRuntime.requestUpdate(
			testSchema.schema(),
			List.of(new ConfigValueUpdate<>(testSchema.enabled(), false))
		);
		AtomicInteger completions = new AtomicInteger();
		AtomicReference<Thread> completionThread = new AtomicReference<>();
		future.whenComplete((ignored, throwable) -> {
			completions.incrementAndGet();
			completionThread.set(Thread.currentThread());
		});

		assertFalse(future.isDone());
		Thread timeoutThread = Thread.currentThread();
		ServerConfigRuntime.expireClientRequests(Long.MAX_VALUE);

		CompletionException exception = assertThrows(CompletionException.class, future::join);
		assertTrue(exception.getCause().getMessage().contains("Timed out"));
		assertEquals(1, completions.get());
		assertEquals(timeoutThread, completionThread.get());
		ServerConfigRuntime.onClientDisconnect();
		assertEquals(1, completions.get());
	}

	@Test
	public void sendFailureCompletesRequestWithUsefulFailureExactlyOnce() {
		TestSchema testSchema = createRemoteServerSchema();
		AtomicInteger sentFragments = new AtomicInteger();
		ServerConfigNetworking.setClientSender(payload -> {
			sentFragments.incrementAndGet();
			return false;
		});

		CompletableFuture<Void> future = ServerConfigRuntime.requestUpdate(
			testSchema.schema(),
			List.of(new ConfigValueUpdate<>(testSchema.enabled(), false))
		);
		AtomicInteger completions = new AtomicInteger();
		future.whenComplete((ignored, throwable) -> completions.incrementAndGet());

		CompletionException exception = assertThrows(CompletionException.class, future::join);
		assertTrue(exception.getCause().getMessage().contains("does not support"));
		assertEquals(1, sentFragments.get());
		assertEquals(1, completions.get());
		ServerConfigRuntime.onClientDisconnect();
		assertEquals(1, completions.get());
	}

	@Test
	public void responseForDifferentSchemaFailsPendingRequest() {
		TestSchema testSchema = createRemoteServerSchema();
		List<byte[]> sentChunks = new java.util.ArrayList<>();
		ServerConfigNetworking.setClientSender(payload -> {
			sentChunks.add(payload.payload());
			return true;
		});
		CompletableFuture<Void> future = ServerConfigRuntime.requestUpdate(
			testSchema.schema(),
			List.of(new ConfigValueUpdate<>(testSchema.enabled(), false))
		);
		ServerConfigPayloadReassembler reassembler = new ServerConfigPayloadReassembler();
		byte[] encoded = sentChunks.stream()
			.map(reassembler::accept)
			.flatMap(java.util.Optional::stream)
			.findFirst()
			.orElseThrow();
		ServerConfigUpdatePayload sent = ServerConfigPayloadCodec.decodeUpdate(encoded);

		ServerConfigRuntime.handleSync(new ServerConfigSyncPayload(
			new ServerConfigKey("different_mod", "server.ini"),
			sent.requestId(),
			true,
			false,
			"",
			List.of()
		));

		CompletionException exception = assertThrows(CompletionException.class, future::join);
		assertTrue(exception.getCause().getMessage().contains("does not match"));
	}

	@Test
	public void registrationEnforcesServerSnapshotValueCount() {
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

		manager.registerSchema(maximum);
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> manager.registerSchema(excessive)
		);

		assertTrue(exception.getMessage().contains("cannot be synchronized"));
		assertTrue(exception.getMessage().contains("Too many server config values"));
		assertEquals(List.of(maximum), List.copyOf(manager.getSchemas()));
	}

	@Test
	public void registrationEnforcesSerializedValueAndTotalSnapshotLengths() {
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
		ConfigSchema largeSnapshot = createStringServerSchema(
				new ServerConfigKey("large_snapshot", "server.ini"),
				() -> Optional.empty(),
				3,
				"x".repeat(160 * 1024)
			)
			.schema();
		ConfigSchema excessiveSnapshot = createStringServerSchema(
				new ServerConfigKey("excessive_snapshot", "server.ini"),
				() -> Optional.empty(),
				3,
				"x".repeat(180 * 1024)
			)
			.schema();

		manager.registerSchema(maximumValue);
		manager.registerSchema(largeSnapshot);
		IllegalArgumentException valueException = assertThrows(
			IllegalArgumentException.class,
			() -> manager.registerSchema(excessiveValue)
		);
		IllegalArgumentException totalException = assertThrows(
			IllegalArgumentException.class,
			() -> manager.registerSchema(excessiveSnapshot)
		);

		assertTrue(valueException.getMessage().contains("serialized effective value"));
		assertTrue(totalException.getMessage().contains("maximum length"));
		assertEquals(List.of(maximumValue, largeSnapshot), List.copyOf(manager.getSchemas()));
	}

	@Test
	public void oversizedServerUpdateIsRejectedBeforeMutation(@TempDir Path tempDir) {
		TestStringSchema testSchema = createStringServerSchema(
			new ServerConfigKey("update_test", "server.ini"),
			() -> Optional.of(tempDir.resolve("server.ini")),
			1,
			"original"
		);
		testSchema.schema().register(null, false);

		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> testSchema.schema().applyServerUpdates(List.of(new ConfigValueUpdate<>(
				testSchema.values().getFirst(),
				"x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES + 1)
			)))
		);

		assertTrue(exception.getMessage().contains("cannot be synchronized"));
		assertEquals("original", testSchema.values().getFirst().getValue());
		assertEquals("original", testSchema.values().getFirst().getPendingValue());
	}

	@Test
	public void remoteUpdateRequestRejectsOversizedSnapshotBeforeSending() {
		TestStringSchema testSchema = createStringServerSchema(
			new ServerConfigKey("remote_update_test", "server.ini"),
			() -> Optional.empty(),
			1,
			"original"
		);
		testSchema.schema().applyRemoteSnapshot(
			List.of(new ServerConfigValueData("general", "value_0", "original")),
			true
		);
		AtomicInteger sentFragments = new AtomicInteger();
		ServerConfigNetworking.setClientSender(payload -> {
			sentFragments.incrementAndGet();
			return true;
		});

		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> testSchema.schema().requestBatchUpdate(updater -> updater.set(
				testSchema.values().getFirst(),
				"x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES + 1)
			))
		);

		assertTrue(exception.getMessage().contains("cannot be synchronized"));
		assertEquals(0, sentFragments.get());
		assertEquals("original", testSchema.values().getFirst().getValue());
	}

	@Test
	public void updateRejectsSnapshotThatWouldBeOversizedAfterRestart(@TempDir Path tempDir) {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		List<ConfigValue<String>> values = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
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
		String prospectiveValue = "x".repeat(180 * 1024);
		List<ConfigValueUpdate<?>> updates = new ArrayList<>();
		values.forEach(value -> updates.add(new ConfigValueUpdate<>(value, prospectiveValue)));

		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> schema.applyServerUpdates(updates)
		);

		assertTrue(exception.getMessage().contains("maximum length"));
		assertTrue(values.stream().allMatch(value -> value.getValue().length() == 80 * 1024));
		assertTrue(values.stream().allMatch(value -> value.getPendingValue().length() == 80 * 1024));
	}

	@Test
	public void oversizedServerFileReloadKeepsPreviousSnapshot(@TempDir Path tempDir) throws IOException {
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
		testSchema.schema().addListener(ignored -> notifications.incrementAndGet());
		Files.writeString(
			oversizedPath,
			"[general]\nvalue_0 = " + "x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES + 1) + "\n"
		);

		activePath.set(Optional.of(oversizedPath));

		assertEquals(Optional.of(oversizedPath), testSchema.schema().getPath());
		assertEquals("original", testSchema.values().getFirst().getValue());
		assertEquals(0, notifications.get());
	}

	@Test
	public void registrationRejectsOversizedInitialFileWithoutPublishing(@TempDir Path tempDir) throws IOException {
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

		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> manager.registerSchema(testSchema.schema())
		);

		assertTrue(exception.getMessage().contains("cannot be synchronized"));
		assertEquals(List.of(), List.copyOf(manager.getSchemas()));
		assertEquals("original", testSchema.values().getFirst().getEffectiveValueWithoutLoading());
		assertTrue(Files.readString(path).contains(oversizedValue));
	}

	private static TestSchema createRemoteServerSchema() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = new ConfigSchema(
			"test_mod",
			() -> java.util.Optional.empty(),
			List.of(builder),
			List.of(builder),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigSchemaType.SERVER,
			new ServerConfigKey("test_mod", "server.ini")
		);
		return new TestSchema(schema, enabled);
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

	private record TestSchema(ConfigSchema schema, ConfigValue<Boolean> enabled) {}

	private record TestStringSchema(ConfigSchema schema, List<ConfigValue<String>> values) {}
}
