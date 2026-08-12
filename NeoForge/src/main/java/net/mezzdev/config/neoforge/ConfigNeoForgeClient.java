package net.mezzdev.config.neoforge;

import net.mezzdev.config.plugin.ConfigPluginLoader;
import net.mezzdev.config.plugin.ServerConfigPluginLoader;
import net.mezzdev.config.server.ServerConfigNetworking;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ConfigNeoForgeClient {
	private ConfigNeoForgeClient() {

	}

	public static void register() {
		Path configRootDir = FMLPaths.CONFIGDIR.get();
		ServerConfigPluginLoader.createIntegratedServerConfigManager(
			"MezzConfig Integrated Server File Watcher",
			configRootDir,
			ConfigNeoForgePluginFinder.getServerPlugins()
		);
		ConfigPluginLoader.createConfigManager(
			"MezzConfig File Watcher",
			configRootDir,
			!FMLLoader.isProduction(),
			ConfigNeoForgePluginFinder.getPlugins(),
			ConfigNeoForgePluginFinder.getServerPlugins()
		);
		ServerConfigNetworking.setClientSender(payload -> {
			ClientPacketListener connection = Minecraft.getInstance().getConnection();
			if (connection == null || !connection.hasChannel(payload.type())) {
				return false;
			}
			PacketDistributor.sendToServer(payload);
			return true;
		});
		NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ServerConfigRuntime.onClientTick());
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ServerConfigRuntime.onClientDisconnect());
	}
}
