package net.mezzdev.config.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.mezzdev.config.plugin.ConfigPluginLoader;
import net.mezzdev.config.plugin.ServerConfigPluginLoader;
import net.mezzdev.config.server.ServerConfigNetworking;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.server.ServerConfigSyncChunkPayload;

import java.nio.file.Path;

/**
 * Fabric client entry point for the config mod.
 */
public final class ConfigFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ConfigFabric.registerCommonServerSupport();
		FabricLoader fabricLoader = FabricLoader.getInstance();
		Path configRootDir = fabricLoader
			.getConfigDir();
		ServerConfigPluginLoader.createIntegratedServerConfigManager(
			"MezzConfig Integrated Server File Watcher",
			configRootDir,
			ConfigFabricPluginFinder.getServerPlugins()
		);
		ConfigPluginLoader.createConfigManager(
			"MezzConfig File Watcher",
			configRootDir,
			fabricLoader.isDevelopmentEnvironment(),
			ConfigFabricPluginFinder.getPlugins(),
			ConfigFabricPluginFinder.getServerPlugins()
		);
		ClientPlayNetworking.registerGlobalReceiver(
			ServerConfigSyncChunkPayload.TYPE,
			(payload, context) -> ServerConfigRuntime.handleSyncChunk(payload)
		);
		ServerConfigNetworking.setClientSender(payload -> {
			if (!ClientPlayNetworking.canSend(payload.type())) {
				return false;
			}
			ClientPlayNetworking.send(payload);
			return true;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> ServerConfigRuntime.onClientTick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ServerConfigRuntime.onClientDisconnect());
	}
}
