package net.mezzdev.config.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public final class ServerConfigNetworking {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Object SERVER_SEND_LOCK = new Object();

	private ServerConfigNetworking() {

	}

	static boolean sendToPlayer(ServerConfigRuntime.Player player, ServerIdentityPayload payload) {
		try {
			synchronized (SERVER_SEND_LOCK) {
				return player.sendIdentity(payload.serverId());
			}
		} catch (RuntimeException e) {
			LOGGER.warn("Failed to send server identity to {}.", player.getName(), e);
			return false;
		}
	}

	static boolean sendToPlayer(ServerConfigRuntime.Player player, ServerConfigSyncPayload payload) {
		try {
			byte[] encoded = ServerConfigPayloadCodec.encodeSync(payload);
			List<byte[]> chunks = ServerConfigPayloadChunker.split(encoded);
			synchronized (SERVER_SEND_LOCK) {
				for (byte[] chunk : chunks) {
					if (!player.sendSync(chunk)) {
						return false;
					}
				}
			}
			return true;
		} catch (RuntimeException e) {
			LOGGER.warn("Failed to send server config payload {} to {}.", payload.key(), player.getName(), e);
			return false;
		}
	}

}
