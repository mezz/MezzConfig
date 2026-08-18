package net.mezzdev.config.server;

import net.mezzdev.config.api.schema.ConfigOwnership;
import net.mezzdev.config.api.schema.ConfigScope;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.value.ConfigValueUpdate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
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
			ConfigOwnership.SERVER,
			ConfigScope.WORLD,
			new ServerConfigKey("test_mod", "server.ini")
		);
		return new TestSchema(schema, enabled);
	}

	private record TestSchema(ConfigSchema schema, ConfigValue<Boolean> enabled) {}
}
