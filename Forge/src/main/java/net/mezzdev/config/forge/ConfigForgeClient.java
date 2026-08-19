package net.mezzdev.config.forge;

import net.mezzdev.config.server.ServerConfigRuntime;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

public final class ConfigForgeClient {
	private ConfigForgeClient() {

	}

	public static void register() {
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				ServerConfigRuntime.onClientTick();
			}
		});
		MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> ServerConfigRuntime.onClientWorldStarted());
		MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ServerConfigRuntime.onClientDisconnect());
	}
}
