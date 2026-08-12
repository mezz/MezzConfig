package net.mezzdev.config.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerConfigPayloadCodecTest {
	@Test
	public void largeSyncPayloadRoundTripsThroughChunks() {
		List<ServerConfigValueData> values = createLargeValueList();
		ServerConfigSyncPayload payload = new ServerConfigSyncPayload(
			new ServerConfigKey("test_mod", "large-server-config.ini"),
			42,
			true,
			true,
			"",
			values
		);

		byte[] encoded = ServerConfigPayloadCodec.encodeSync(payload);
		List<byte[]> chunks = ServerConfigPayloadChunker.split(encoded);
		Optional<byte[]> reassembled = reassemble(chunks);

		assertTrue(chunks.size() > 1);
		assertTrue(chunks.stream().allMatch(chunk -> chunk.length <= ServerConfigPayloadChunker.MAX_NETWORK_PAYLOAD_LENGTH));
		assertEquals(payload, ServerConfigPayloadCodec.decodeSync(reassembled.orElseThrow()));
	}

	@Test
	public void largeUpdatePayloadRoundTripsThroughChunks() {
		ServerConfigUpdatePayload payload = new ServerConfigUpdatePayload(
			new ServerConfigKey("test_mod", "large-server-config.ini"),
			77,
			createLargeValueList()
		);

		byte[] encoded = ServerConfigPayloadCodec.encodeUpdate(payload);
		List<byte[]> chunks = ServerConfigPayloadChunker.split(encoded);
		Optional<byte[]> reassembled = reassemble(chunks);

		assertTrue(chunks.size() > 1);
		assertEquals(payload, ServerConfigPayloadCodec.decodeUpdate(reassembled.orElseThrow()));
	}

	private static List<ServerConfigValueData> createLargeValueList() {
		List<ServerConfigValueData> values = new ArrayList<>();
		values.add(new ServerConfigValueData("general", "largeText", "設定値".repeat(20_000)));
		for (int i = 0; i < 5_000; i++) {
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
}
