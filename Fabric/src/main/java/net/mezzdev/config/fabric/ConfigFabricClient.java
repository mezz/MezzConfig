package net.mezzdev.config.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.mezzdev.config.server.ServerConfigRuntime;

/**
 * Fabric client entry point for the config mod.
 */
public final class ConfigFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ConfigFabricClientNetwork.register();
		ClientTickEvents.END_CLIENT_TICK.register(client -> ServerConfigRuntime.onClientTick());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ServerConfigRuntime.onClientWorldStarted());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ServerConfigRuntime.onClientDisconnect());
	}
}
