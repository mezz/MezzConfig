package net.mezzdev.config.neoforge;

import net.mezzdev.config.server.ServerConfigNetworking;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.server.ServerConfigSyncChunkPayload;
import net.mezzdev.config.server.ServerIdentityPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class ConfigNeoForgeNetwork {
	private static final String PROTOCOL_VERSION = "2";

	private ConfigNeoForgeNetwork() {

	}

	public static void register(IEventBus modEventBus) {
		modEventBus.addListener(ConfigNeoForgeNetwork::registerPayloads);
		ServerConfigNetworking.setServerSender((player, payload) -> {
			if (player.connection.hasChannel(payload.type())) {
				PacketDistributor.sendToPlayer(player, payload);
				return true;
			}
			return false;
		});
	}

	private static void registerPayloads(RegisterPayloadHandlersEvent event) {
		event.registrar(PROTOCOL_VERSION)
			.executesOn(HandlerThread.MAIN)
			.optional()
			.playToClient(
				ServerIdentityPayload.TYPE,
				ServerIdentityPayload.STREAM_CODEC,
				(payload, context) -> ServerConfigRuntime.handleServerIdentity(payload)
			)
			.playToClient(
				ServerConfigSyncChunkPayload.TYPE,
				ServerConfigSyncChunkPayload.STREAM_CODEC,
				(payload, context) -> ServerConfigRuntime.handleSyncChunk(payload)
			);
	}
}
