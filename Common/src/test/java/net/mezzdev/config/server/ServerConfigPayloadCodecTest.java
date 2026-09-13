package net.mezzdev.config.server;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerConfigPayloadCodecTest {
	@Test
	public void syncPayloadPreservesEffectiveValues() {
		// Setup: a synchronization payload contains one server config value.
		ServerConfigSyncPayload payload = new ServerConfigSyncPayload(
			new ServerConfigKey("test_mod", "server.ini"),
			List.of(new ServerConfigValueData("general", "afterRestart", "false"))
		);

		// Operation and assertions: encoding and decoding preserve the complete payload.
		assertEquals(payload, ServerConfigPayloadCodec.decodeSync(ServerConfigPayloadCodec.encodeSync(payload)));
	}

	@Test
	public void largeSyncPayloadRoundTripsThroughChunks() {
		// Setup: a valid synchronization payload is too large for one network fragment.
		List<ServerConfigValueData> values = createLargeValueList();
		ServerConfigSyncPayload payload = new ServerConfigSyncPayload(
			new ServerConfigKey("test_mod", "large-server-config.ini"),
			values
		);

		// Operation: encode, split, and reassemble the payload.
		byte[] encoded = ServerConfigPayloadCodec.encodeSync(payload);
		List<byte[]> chunks = ServerConfigPayloadChunker.split(encoded);
		Optional<byte[]> reassembled = reassemble(chunks);

		// Assertions: bounded fragments reconstruct the original logical payload.
		assertTrue(chunks.size() > 1);
		assertTrue(chunks.size() <= ServerConfigPayloadChunker.MAX_FRAGMENT_COUNT);
		assertTrue(chunks.stream().allMatch(chunk -> chunk.length <= ServerConfigPayloadChunker.MAX_NETWORK_PAYLOAD_LENGTH));
		assertEquals(payload, ServerConfigPayloadCodec.decodeSync(reassembled.orElseThrow()));
	}

	@Test
	public void interleavedMessagesReassembleIndependently() {
		// Setup: two multi-fragment messages have distinct IDs and recognizable byte patterns.
		byte[] first = new byte[ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH + 7];
		byte[] second = new byte[ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH + 11];
		for (int i = 0; i < first.length; i++) {
			first[i] = (byte) i;
		}
		for (int i = 0; i < second.length; i++) {
			second[i] = (byte) (i * 3);
		}
		List<byte[]> firstChunks = ServerConfigPayloadChunker.split(first, 101);
		List<byte[]> secondChunks = ServerConfigPayloadChunker.split(second, 202);
		ServerConfigPayloadReassembler reassembler = new ServerConfigPayloadReassembler();

		// Operation: interleave fragments from both messages in one reassembler.
		assertTrue(reassembler.accept(firstChunks.getFirst()).isEmpty());
		assertTrue(reassembler.accept(secondChunks.getFirst()).isEmpty());

		// Assertions: each final fragment completes only its own message and releases all pending state.
		assertArrayEquals(first, reassembler.accept(firstChunks.getLast()).orElseThrow());
		assertArrayEquals(second, reassembler.accept(secondChunks.getLast()).orElseThrow());
		assertTrue(reassembler.isEmpty());
	}

	@Test
	public void duplicateFragmentRejectsAndDiscardsItsMessage() {
		// Setup: a two-fragment message has begun reassembly.
		byte[] data = new byte[ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH + 1];
		List<byte[]> chunks = ServerConfigPayloadChunker.split(data, 303);
		ServerConfigPayloadReassembler reassembler = new ServerConfigPayloadReassembler();

		// Operation: submit its first fragment twice.
		assertTrue(reassembler.accept(chunks.getFirst()).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> reassembler.accept(chunks.getFirst()));

		// Assertions: the duplicate discards prior state, so the remaining fragment starts a fresh message.
		assertTrue(reassembler.isEmpty());
		assertTrue(reassembler.accept(chunks.getLast()).isEmpty());
		assertEquals(1, reassembler.pendingMessageCount());
	}

	@Test
	public void inconsistentMessageMetadataRejectsAndDiscardsItsMessage() {
		// Setup: two messages reuse an ID while declaring different total lengths.
		byte[] first = new byte[ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH + 1];
		byte[] second = new byte[ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH + 2];
		List<byte[]> firstChunks = ServerConfigPayloadChunker.split(first, 404);
		List<byte[]> secondChunks = ServerConfigPayloadChunker.split(second, 404);
		ServerConfigPayloadReassembler reassembler = new ServerConfigPayloadReassembler();

		// Operation: start the first message and then submit a fragment with conflicting metadata.
		assertTrue(reassembler.accept(firstChunks.getFirst()).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> reassembler.accept(secondChunks.getLast()));

		// Assertions: the metadata conflict discards the pending message.
		assertTrue(reassembler.isEmpty());
	}

	@Test
	public void incompleteMessagesExpire() {
		// Setup: a multi-fragment message has only its first fragment at a known timestamp.
		byte[] data = new byte[ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH + 1];
		List<byte[]> chunks = ServerConfigPayloadChunker.split(data, 505);
		ServerConfigPayloadReassembler reassembler = new ServerConfigPayloadReassembler();

		// Operation: advance expiration processing to the incomplete-message deadline.
		assertTrue(reassembler.accept(chunks.getFirst(), 1).isEmpty());
		assertEquals(1, reassembler.pendingMessageCount());
		assertEquals(1, reassembler.expire(1 + ServerConfigPayloadReassembler.INCOMPLETE_MESSAGE_TIMEOUT.toNanos()));

		// Assertions: the expired message releases its pending state.
		assertTrue(reassembler.isEmpty());
	}

	@Test
	public void incompleteMessageReportsTimeUntilNextExpiration() {
		// Setup: two incomplete messages begin five nanoseconds apart.
		byte[] data = new byte[ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH + 1];
		byte[] firstChunk = ServerConfigPayloadChunker.split(data, 506).getFirst();
		byte[] laterChunk = ServerConfigPayloadChunker.split(data, 507).getFirst();
		ServerConfigPayloadReassembler reassembler = new ServerConfigPayloadReassembler();
		long createdNanos = 100;
		long timeoutNanos = ServerConfigPayloadReassembler.INCOMPLETE_MESSAGE_TIMEOUT.toNanos();

		// Operation: accept both messages and query or expire them as their deadlines arrive.
		assertTrue(reassembler.accept(firstChunk, createdNanos).isEmpty());
		assertTrue(reassembler.accept(laterChunk, createdNanos + 5).isEmpty());
		assertEquals(
			ServerConfigPayloadReassembler.INCOMPLETE_MESSAGE_TIMEOUT,
			reassembler.getTimeUntilNextExpiration(createdNanos).orElseThrow()
		);
		assertEquals(
			Duration.ZERO,
			reassembler.getTimeUntilNextExpiration(createdNanos + timeoutNanos).orElseThrow()
		);
		assertEquals(1, reassembler.expire(createdNanos + timeoutNanos));

		// Assertions: the second deadline is reported precisely, then no expiration remains.
		assertEquals(Duration.ofNanos(5), reassembler.getTimeUntilNextExpiration(createdNanos + timeoutNanos).orElseThrow());
		assertEquals(1, reassembler.expire(createdNanos + timeoutNanos + 5));
		assertTrue(reassembler.getTimeUntilNextExpiration(Long.MAX_VALUE).isEmpty());
	}

	@Test
	public void concurrentMessageLimitBoundsAllocations() {
		// Setup: the reassembler receives one fragment for every allowed concurrent message.
		ServerConfigPayloadReassembler reassembler = new ServerConfigPayloadReassembler();
		byte[] data = new byte[ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH + 1];
		for (long messageId = 1; messageId <= ServerConfigPayloadReassembler.MAX_CONCURRENT_MESSAGES; messageId++) {
			byte[] firstChunk = ServerConfigPayloadChunker.split(data, messageId).getFirst();
			assertTrue(reassembler.accept(firstChunk, 1).isEmpty());
		}

		// Operation: start one additional message beyond the concurrency limit.
		byte[] excessive = ServerConfigPayloadChunker.split(data, 99).getFirst();
		assertThrows(IllegalArgumentException.class, () -> reassembler.accept(excessive, 1));

		// Assertions: rejection does not allocate state beyond the configured bound.
		assertEquals(ServerConfigPayloadReassembler.MAX_CONCURRENT_MESSAGES, reassembler.pendingMessageCount());
	}

	@Test
	public void oversizedPayloadIsRejectedBeforeChunking() {
		// Operation and assertions: splitting rejects payloads beyond the maximum reassembled length.
		assertThrows(
			IllegalArgumentException.class,
			() -> ServerConfigPayloadChunker.split(new byte[ServerConfigPayloadChunker.MAX_REASSEMBLED_PAYLOAD_LENGTH + 1])
		);
	}

	@Test
	public void truncatedFragmentIsRejectedDuringReassembly() {
		// Setup: a valid one-byte fragment is truncated after encoding.
		byte[] valid = ServerConfigPayloadChunker.split(new byte[1], 606).getFirst();
		byte[] truncated = new byte[valid.length - 1];
		System.arraycopy(valid, 0, truncated, 0, truncated.length);

		// Operation and assertions: reassembly rejects the incomplete fragment envelope.
		assertThrows(IllegalArgumentException.class, () -> new ServerConfigPayloadReassembler().accept(truncated));
	}

	@Test
	public void unsupportedChunkEnvelopeVersionIsRejected() {
		// Setup: an otherwise valid fragment declares a future envelope version.
		byte[] chunk = ServerConfigPayloadChunker.split(new byte[1], 607).getFirst();
		assertEquals(ServerConfigProtocol.CHUNK_ENVELOPE_VERSION, chunk[Integer.BYTES]);
		chunk[Integer.BYTES] = (byte) (ServerConfigProtocol.CHUNK_ENVELOPE_VERSION + 1);

		// Operation: submit the unsupported envelope for reassembly.
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> new ServerConfigPayloadReassembler().accept(chunk)
		);

		// Assertions: rejection identifies the unsupported version contract.
		assertTrue(exception.getMessage().contains("Unsupported server config fragment version"));
	}

	@Test
	public void codecRejectsExcessiveValueCountsBeforeAllocatingAList() throws IOException {
		// Setup: a compact forged payload declares more values than the codec permits.
		byte[] encoded;
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream output = new DataOutputStream(bytes)
		) {
			writeString(output, "test_mod");
			writeString(output, "server.ini");
			output.writeInt(ServerConfigPayloadCodec.MAX_VALUE_COUNT + 1);
			encoded = bytes.toByteArray();
		}

		// Operation and assertions: decoding rejects the count before allocating its claimed list.
		assertThrows(IllegalArgumentException.class, () -> ServerConfigPayloadCodec.decodeSync(encoded));
	}

	@Test
	public void codecRejectsOversizedStringsBeforeAllocatingThem() throws IOException {
		// Setup: a compact forged payload declares an impossible string length.
		byte[] encoded;
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream output = new DataOutputStream(bytes)
		) {
			writeString(output, "test_mod");
			writeString(output, "server.ini");
			output.writeInt(1);
			output.writeInt(Integer.MAX_VALUE);
			encoded = bytes.toByteArray();
		}

		// Operation and assertions: decoding rejects the length before allocating the claimed string.
		assertThrows(IllegalArgumentException.class, () -> ServerConfigPayloadCodec.decodeSync(encoded));
	}

	@Test
	public void codecRejectsTooManyValuesWhenEncoding() {
		// Setup: one payload exceeds the value-count limit.
		ServerConfigValueData value = new ServerConfigValueData("general", "enabled", "true");
		ServerConfigSyncPayload tooManyValues = new ServerConfigSyncPayload(
			new ServerConfigKey("test_mod", "server.ini"),
			Collections.nCopies(ServerConfigPayloadCodec.MAX_VALUE_COUNT + 1, value)
		);

		// Operation and assertions: encoding rejects the value count before creating network data.
		assertThrows(IllegalArgumentException.class, () -> ServerConfigPayloadCodec.encodeSync(tooManyValues));
	}

	@Test
	public void codecRejectsOversizedSerializedValuesWhenEncoding() {
		// Setup: one payload exceeds the per-value byte limit.
		ServerConfigSyncPayload oversizedValue = new ServerConfigSyncPayload(
			new ServerConfigKey("test_mod", "server.ini"),
			List.of(new ServerConfigValueData(
				"general",
				"value",
				"x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES + 1)
			))
		);

		// Operation and assertions: encoding rejects the serialized value before creating network data.
		assertThrows(IllegalArgumentException.class, () -> ServerConfigPayloadCodec.encodeSync(oversizedValue));
	}

	@Test
	public void codecRejectsAggregatePayloadsWithIndividuallyValidValues() {
		// Setup: several individually valid maximum-sized values exceed the aggregate payload limit.
		String maximumValue = "x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES);
		List<ServerConfigValueData> values = List.of(
			new ServerConfigValueData("general", "first", maximumValue),
			new ServerConfigValueData("general", "second", maximumValue),
			new ServerConfigValueData("general", "third", maximumValue),
			new ServerConfigValueData("general", "fourth", maximumValue)
		);
		ServerConfigSyncPayload payload = new ServerConfigSyncPayload(
			new ServerConfigKey("test_mod", "server.ini"),
			values
		);

		// Operation and assertions: encoding enforces the aggregate bound across all values.
		assertThrows(IllegalArgumentException.class, () -> ServerConfigPayloadCodec.encodeSync(payload));
	}

	private static List<ServerConfigValueData> createLargeValueList() {
		List<ServerConfigValueData> values = new ArrayList<>();
		values.add(new ServerConfigValueData("general", "largeText", "設定値".repeat(20_000)));
		for (int i = 0; i < 2_000; i++) {
			values.add(new ServerConfigValueData("generated", "value_" + i, Integer.toString(i)));
		}
		return List.copyOf(values);
	}

	private static Optional<byte[]> reassemble(List<byte[]> chunks) {
		ServerConfigPayloadReassembler reassembler = new ServerConfigPayloadReassembler();
		Optional<byte[]> result = Optional.empty();
		for (byte[] chunk : chunks) {
			result = reassembler.accept(chunk);
		}
		return result;
	}

	private static void writeString(DataOutputStream output, String value) throws IOException {
		byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		output.writeInt(encoded.length);
		output.write(encoded);
	}
}
