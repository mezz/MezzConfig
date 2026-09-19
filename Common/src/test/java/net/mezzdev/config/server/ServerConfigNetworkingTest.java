package net.mezzdev.config.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConfigNetworkingTest {
	@Test
	void platformSenderPreservesChunkedSnapshot() {
		// Setup: a value large enough to cross packet boundaries.
		ServerConfigSyncPayload snapshot = new ServerConfigSyncPayload(
			new ServerConfigKey("transport_test", "server.ini"),
			List.of(new ServerConfigValueData("general", "value", "x".repeat(80_000)))
		);
		RecordingPlayer player = new RecordingPlayer(true);

		// Operation: send through the platform boundary and reassemble the received bytes.
		boolean sent = ServerConfigNetworking.sendToPlayer(player, snapshot);
		ServerConfigPayloadReassembler receiver = new ServerConfigPayloadReassembler();
		List<ServerConfigSyncPayload> received = new ArrayList<>();
		player.fragments.forEach(fragment -> receiver.accept(fragment)
			.map(ServerConfigPayloadCodec::decodeSync)
			.ifPresent(received::add));

		// Assertions: fragmentation is lossless and delivers exactly one complete snapshot.
		assertTrue(sent);
		assertTrue(player.fragments.size() > 1);
		assertEquals(List.of(snapshot), received);
	}

	@Test
	void unavailableOptionalChannelStopsAfterFirstFragment() {
		// Setup: the other endpoint does not have the optional config channel.
		ServerConfigSyncPayload snapshot = new ServerConfigSyncPayload(
			new ServerConfigKey("transport_test", "server.ini"),
			List.of(new ServerConfigValueData("general", "value", "x".repeat(80_000)))
		);
		RecordingPlayer player = new RecordingPlayer(false);

		// Operation: attempt to send a multi-fragment snapshot to that endpoint.
		boolean sent = ServerConfigNetworking.sendToPlayer(player, snapshot);

		// Assertions: no remaining fragments are attempted after the channel reports absence.
		assertFalse(sent);
		assertEquals(1, player.attempts);
		assertTrue(player.fragments.isEmpty());
	}

	private static final class RecordingPlayer implements ServerConfigRuntime.Player {
		private final boolean available;
		private final List<byte[]> fragments = new ArrayList<>();
		private int attempts;

		private RecordingPlayer(boolean available) {
			this.available = available;
		}

		@Override
		public String getName() {
			return "test player";
		}

		@Override
		public boolean sendIdentity(UUID serverId) {
			return available;
		}

		@Override
		public boolean sendSync(byte[] data) {
			attempts++;
			if (available) {
				fragments.add(data.clone());
			}
			return available;
		}
	}
}
