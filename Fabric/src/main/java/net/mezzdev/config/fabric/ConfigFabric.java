package net.mezzdev.config.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mezzdev.config.server.ServerConfigNetworking;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.server.ServerConfigSyncChunkPayload;
import net.mezzdev.config.server.ServerConfigUpdateChunkPayload;

/**
 * Fabric common entry point and server networking registration.
 */
public final class ConfigFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		registerCommonServerSupport();
	}

	public static void registerCommonServerSupport() {
		PayloadTypeRegistry.playS2C().register(ServerConfigSyncChunkPayload.TYPE, ServerConfigSyncChunkPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(ServerConfigUpdateChunkPayload.TYPE, ServerConfigUpdateChunkPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(
			ServerConfigUpdateChunkPayload.TYPE,
			(payload, context) -> ServerConfigRuntime.handleUpdateChunk(context.player(), payload)
		);
		ServerConfigNetworking.setServerSender((player, payload) -> {
			if (ServerPlayNetworking.canSend(player, payload.type())) {
				ServerPlayNetworking.send(player, payload);
				return true;
			}
			return false;
		});
		ServerLifecycleEvents.SERVER_STARTED.register(ServerConfigRuntime::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerConfigRuntime.onServerStopped());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerConfigRuntime.onPlayerJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerConfigRuntime.onPlayerDisconnect(handler.player));
	}
}
