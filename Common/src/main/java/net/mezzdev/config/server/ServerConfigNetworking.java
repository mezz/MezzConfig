package net.mezzdev.config.server;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.mezzdev.config.util.ErrorUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiPredicate;

public final class ServerConfigNetworking {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Object SERVER_SEND_LOCK = new Object();
	private static @Nullable BiPredicate<ServerPlayer, CustomPacketPayload> serverSender;

	private ServerConfigNetworking() {

	}

	public static void setServerSender(BiPredicate<ServerPlayer, CustomPacketPayload> serverSender) {
		ServerConfigNetworking.serverSender = ErrorUtil.checkNotNull(serverSender, "serverSender");
	}

	static boolean sendToPlayer(ServerPlayer player, ServerIdentityPayload payload) {
		try {
			synchronized (SERVER_SEND_LOCK) {
				return sendPayload(player, payload);
			}
		} catch (RuntimeException e) {
			LOGGER.warn("Failed to send server identity to {}.", player.getGameProfile().getName(), e);
			return false;
		}
	}

	static boolean sendToPlayer(ServerPlayer player, ServerConfigSyncPayload payload) {
		try {
			byte[] encoded = ServerConfigPayloadCodec.encodeSync(payload);
			List<byte[]> chunks = ServerConfigPayloadChunker.split(encoded);
			synchronized (SERVER_SEND_LOCK) {
				for (byte[] chunk : chunks) {
					if (!sendPayload(player, new ServerConfigSyncChunkPayload(chunk))) {
						return false;
					}
				}
			}
			return true;
		} catch (RuntimeException e) {
			LOGGER.warn("Failed to send server config payload {} to {}.", payload.key(), player.getGameProfile().getName(), e);
			return false;
		}
	}

	private static boolean sendPayload(ServerPlayer player, CustomPacketPayload payload) {
		BiPredicate<ServerPlayer, CustomPacketPayload> sender = serverSender;
		return sender != null && sender.test(player, payload);
	}
}
