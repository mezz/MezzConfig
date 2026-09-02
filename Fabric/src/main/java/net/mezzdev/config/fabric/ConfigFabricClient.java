package net.mezzdev.config.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.server.ServerConfigSyncChunkPayload;
import net.mezzdev.config.server.ServerIdentityPayload;

/**
 * Fabric client entry point for the config mod.
 */
public final class ConfigFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(
			ServerIdentityPayload.TYPE,
			(payload, context) -> ServerConfigRuntime.handleServerIdentity(payload)
		);
		ClientPlayNetworking.registerGlobalReceiver(
			ServerConfigSyncChunkPayload.TYPE,
			(payload, context) -> ServerConfigRuntime.handleSyncChunk(payload)
		);
		ClientTickEvents.END_CLIENT_TICK.register(client -> ServerConfigRuntime.onClientTick());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ServerConfigRuntime.onClientWorldStarted());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ServerConfigRuntime.onClientDisconnect());
	}
}
