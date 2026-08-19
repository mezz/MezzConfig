package net.mezzdev.config.server;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
		ServerConfigSyncPayload payload = new ServerConfigSyncPayload(
			new ServerConfigKey("test_mod", "server.ini"),
			List.of(new ServerConfigValueData("general", "afterRestart", "false"))
		);

		assertEquals(payload, ServerConfigPayloadCodec.decodeSync(ServerConfigPayloadCodec.encodeSync(payload)));
	}

	@Test
	public void largeSyncPayloadRoundTripsThroughChunks() {
		List<ServerConfigValueData> values = createLargeValueList();
		ServerConfigSyncPayload payload = new ServerConfigSyncPayload(
			new ServerConfigKey("test_mod", "large-server-config.ini"),
			values
		);

		byte[] encoded = ServerConfigPayloadCodec.encodeSync(payload);
		List<byte[]> chunks = ServerConfigPayloadChunker.split(encoded);
		Optional<byte[]> reassembled = reassemble(chunks);

		assertTrue(chunks.size() > 1);
		assertTrue(chunks.size() <= ServerConfigPayloadChunker.MAX_FRAGMENT_COUNT);
		assertTrue(chunks.stream().allMatch(chunk -> chunk.length <= ServerConfigPayloadChunker.MAX_NETWORK_PAYLOAD_LENGTH));
		assertEquals(payload, ServerConfigPayloadCodec.decodeSync(reassembled.orElseThrow()));
	}

	@Test
	public void interleavedMessagesReassembleIndependently() {
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

		assertTrue(reassembler.accept(firstChunks.getFirst()).isEmpty());
		assertTrue(reassembler.accept(secondChunks.getFirst()).isEmpty());
		assertArrayEquals(first, reassembler.accept(firstChunks.getLast()).orElseThrow());
		assertArrayEquals(second, reassembler.accept(secondChunks.getLast()).orElseThrow());
		assertTrue(reassembler.isEmpty());
	}

	@Test
	public void duplicateFragmentRejectsAndDiscardsItsMessage() {
		byte[] data = new byte[ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH + 1];
		List<byte[]> chunks = ServerConfigPayloadChunker.split(data, 303);
		ServerConfigPayloadReassembler reassembler = new ServerConfigPayloadReassembler();

		assertTrue(reassembler.accept(chunks.getFirst()).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> reassembler.accept(chunks.getFirst()));
		assertTrue(reassembler.isEmpty());
		assertTrue(reassembler.accept(chunks.getLast()).isEmpty());
		assertEquals(1, reassembler.pendingMessageCount());
	}

	@Test
	public void inconsistentMessageMetadataRejectsAndDiscardsItsMessage() {
		byte[] first = new byte[ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH + 1];
		byte[] second = new byte[ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH + 2];
		List<byte[]> firstChunks = ServerConfigPayloadChunker.split(first, 404);
		List<byte[]> secondChunks = ServerConfigPayloadChunker.split(second, 404);
		ServerConfigPayloadReassembler reassembler = new ServerConfigPayloadReassembler();

		assertTrue(reassembler.accept(firstChunks.getFirst()).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> reassembler.accept(secondChunks.getLast()));
		assertTrue(reassembler.isEmpty());
	}

	@Test
	public void incompleteMessagesExpire() {
		byte[] data = new byte[ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH + 1];
		List<byte[]> chunks = ServerConfigPayloadChunker.split(data, 505);
		ServerConfigPayloadReassembler reassembler = new ServerConfigPayloadReassembler();

		assertTrue(reassembler.accept(chunks.getFirst(), 1).isEmpty());
		assertEquals(1, reassembler.pendingMessageCount());
		assertEquals(1, reassembler.expire(1 + ServerConfigPayloadReassembler.INCOMPLETE_MESSAGE_TIMEOUT.toNanos()));
		assertTrue(reassembler.isEmpty());
	}

	@Test
	public void incompleteMessageReportsTimeUntilNextExpiration() {
		byte[] data = new byte[ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH + 1];
		byte[] firstChunk = ServerConfigPayloadChunker.split(data, 506).getFirst();
		byte[] laterChunk = ServerConfigPayloadChunker.split(data, 507).getFirst();
		ServerConfigPayloadReassembler reassembler = new ServerConfigPayloadReassembler();
		long createdNanos = 100;
		long timeoutNanos = ServerConfigPayloadReassembler.INCOMPLETE_MESSAGE_TIMEOUT.toNanos();

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
		assertEquals(Duration.ofNanos(5), reassembler.getTimeUntilNextExpiration(createdNanos + timeoutNanos).orElseThrow());
		assertEquals(1, reassembler.expire(createdNanos + timeoutNanos + 5));
		assertTrue(reassembler.getTimeUntilNextExpiration(Long.MAX_VALUE).isEmpty());
	}

	@Test
	public void concurrentMessageLimitBoundsAllocations() {
		ServerConfigPayloadReassembler reassembler = new ServerConfigPayloadReassembler();
		byte[] data = new byte[ServerConfigPayloadChunker.MAX_CHUNK_DATA_LENGTH + 1];
		for (long messageId = 1; messageId <= ServerConfigPayloadReassembler.MAX_CONCURRENT_MESSAGES; messageId++) {
			byte[] firstChunk = ServerConfigPayloadChunker.split(data, messageId).getFirst();
			assertTrue(reassembler.accept(firstChunk, 1).isEmpty());
		}

		byte[] excessive = ServerConfigPayloadChunker.split(data, 99).getFirst();
		assertThrows(IllegalArgumentException.class, () -> reassembler.accept(excessive, 1));
		assertEquals(ServerConfigPayloadReassembler.MAX_CONCURRENT_MESSAGES, reassembler.pendingMessageCount());
	}

	@Test
	public void oversizedAndTruncatedFragmentsAreRejected() {
		assertThrows(
			IllegalArgumentException.class,
			() -> ServerConfigPayloadChunker.split(new byte[ServerConfigPayloadChunker.MAX_REASSEMBLED_PAYLOAD_LENGTH + 1])
		);
		byte[] valid = ServerConfigPayloadChunker.split(new byte[1], 606).getFirst();
		byte[] truncated = new byte[valid.length - 1];
		System.arraycopy(valid, 0, truncated, 0, truncated.length);

		assertThrows(IllegalArgumentException.class, () -> new ServerConfigPayloadReassembler().accept(truncated));
	}

	@Test
	public void codecRejectsExcessiveValueCountsBeforeAllocatingAList() throws IOException {
		byte[] encoded;
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream output = new DataOutputStream(bytes)
		) {
			writeString(output, "test_mod");
			writeString(output, "server.ini");
			output.writeInt(ServerConfigPayloadCodec.MAX_VALUE_COUNT + 1);
			encoded = bytes.toByteArray();
		}

		assertThrows(IllegalArgumentException.class, () -> ServerConfigPayloadCodec.decodeSync(encoded));
	}

	@Test
	public void codecRejectsOversizedStringsBeforeAllocatingThem() throws IOException {
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

		assertThrows(IllegalArgumentException.class, () -> ServerConfigPayloadCodec.decodeSync(encoded));
	}

	@Test
	public void codecRejectsTooManyValuesAndOversizedSerializedValuesWhenEncoding() {
		ServerConfigValueData value = new ServerConfigValueData("general", "enabled", "true");
		ServerConfigSyncPayload tooManyValues = new ServerConfigSyncPayload(
			new ServerConfigKey("test_mod", "server.ini"),
			Collections.nCopies(ServerConfigPayloadCodec.MAX_VALUE_COUNT + 1, value)
		);
		ServerConfigSyncPayload oversizedValue = new ServerConfigSyncPayload(
			new ServerConfigKey("test_mod", "server.ini"),
			List.of(new ServerConfigValueData(
				"general",
				"value",
				"x".repeat(ServerConfigPayloadCodec.MAX_SERIALIZED_VALUE_BYTES + 1)
			))
		);

		assertThrows(IllegalArgumentException.class, () -> ServerConfigPayloadCodec.encodeSync(tooManyValues));
		assertThrows(IllegalArgumentException.class, () -> ServerConfigPayloadCodec.encodeSync(oversizedValue));
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
		byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		output.writeInt(encoded.length);
		output.write(encoded);
	}
}
