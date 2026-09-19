package net.mezzdev.config.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.minecraft.MinecraftConfigRuntime;

/**
 * Fabric common entry point and server networking registration.
 */
public final class ConfigFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		registerCommonServerSupport();
	}

	public static void registerCommonServerSupport() {
		ConfigFabricNetwork.register();
		ServerLifecycleEvents.SERVER_STARTED.register(MinecraftConfigRuntime::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerConfigRuntime.onServerStopped());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> MinecraftConfigRuntime.onPlayerJoin(handler.player));
	}
}
