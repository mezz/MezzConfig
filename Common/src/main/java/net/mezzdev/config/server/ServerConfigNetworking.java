package net.mezzdev.config.server;

import net.minecraft.server.level.ServerPlayer;
import net.mezzdev.config.util.ErrorUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class ServerConfigNetworking {
	private static final Object SERVER_SEND_LOCK = new Object();
	private static final Object CLIENT_SEND_LOCK = new Object();
	private static @Nullable BiConsumer<ServerPlayer, ServerConfigSyncChunkPayload> serverSender;
	private static @Nullable Predicate<ServerConfigUpdateChunkPayload> clientSender;

	private ServerConfigNetworking() {

	}

	public static void setServerSender(BiConsumer<ServerPlayer, ServerConfigSyncChunkPayload> serverSender) {
		ServerConfigNetworking.serverSender = ErrorUtil.checkNotNull(serverSender, "serverSender");
	}

	public static void setClientSender(Predicate<ServerConfigUpdateChunkPayload> clientSender) {
		ServerConfigNetworking.clientSender = ErrorUtil.checkNotNull(clientSender, "clientSender");
	}

	static void sendToPlayer(ServerPlayer player, ServerConfigSyncPayload payload) {
		BiConsumer<ServerPlayer, ServerConfigSyncChunkPayload> sender = serverSender;
		if (sender != null) {
			byte[] encoded = ServerConfigPayloadCodec.encodeSync(payload);
			List<byte[]> chunks = ServerConfigPayloadChunker.split(encoded);
			synchronized (SERVER_SEND_LOCK) {
				for (byte[] chunk : chunks) {
					sender.accept(player, new ServerConfigSyncChunkPayload(chunk));
				}
			}
		}
	}

	static boolean sendToServer(ServerConfigUpdatePayload payload) {
		Predicate<ServerConfigUpdateChunkPayload> sender = clientSender;
		if (sender == null) {
			return false;
		}
		byte[] encoded = ServerConfigPayloadCodec.encodeUpdate(payload);
		List<byte[]> chunks = ServerConfigPayloadChunker.split(encoded);
		synchronized (CLIENT_SEND_LOCK) {
			for (byte[] chunk : chunks) {
				if (!sender.test(new ServerConfigUpdateChunkPayload(chunk))) {
					return false;
				}
			}
		}
		return true;
	}
}
