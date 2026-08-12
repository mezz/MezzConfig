package net.mezzdev.config.fabric;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.mezzdev.config.plugin.ServerConfigPluginLoader;
import net.mezzdev.config.server.ServerConfigNetworking;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.server.ServerConfigSyncChunkPayload;
import net.mezzdev.config.server.ServerConfigUpdateChunkPayload;

/**
 * Fabric dedicated-server entry point and common networking registration.
 */
public final class ConfigFabric implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		FabricLoader fabricLoader = FabricLoader.getInstance();
		ServerConfigPluginLoader.createServerConfigManager(
			"MezzConfig Server File Watcher",
			fabricLoader.getConfigDir(),
			ConfigFabricPluginFinder.getServerPlugins()
		);
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
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(ServerConfigRuntime::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerConfigRuntime.onServerStopped());
		ServerTickEvents.END_SERVER_TICK.register(server -> ServerConfigRuntime.onServerTick());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerConfigRuntime.onPlayerJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerConfigRuntime.onPlayerDisconnect(handler.player));
	}
}
