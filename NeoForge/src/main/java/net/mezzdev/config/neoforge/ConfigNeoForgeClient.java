package net.mezzdev.config.neoforge;

import net.mezzdev.config.server.ServerConfigRuntime;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ConfigNeoForgeClient {
	private ConfigNeoForgeClient() {

	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ServerConfigRuntime.onClientTick());
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> ServerConfigRuntime.onClientWorldStarted());
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ServerConfigRuntime.onClientDisconnect());
	}
}
