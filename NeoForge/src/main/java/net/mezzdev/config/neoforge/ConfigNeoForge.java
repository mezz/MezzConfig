package net.mezzdev.config.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.mezzdev.config.plugin.ServerConfigPluginLoader;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge entry point for the config mod.
 */
@Mod(ConfigNeoForge.MOD_ID)
public final class ConfigNeoForge {
	public static final String MOD_ID = "mezz_config";

	public ConfigNeoForge(IEventBus modEventBus, Dist dist) {
		ConfigNeoForgeNetwork.register(modEventBus);
		NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> ServerConfigRuntime.onServerStarted(event.getServer()));
		NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> ServerConfigRuntime.onServerStopped());
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> ServerConfigRuntime.onServerTick());
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				ServerConfigRuntime.onPlayerJoin(player);
			}
		});
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				ServerConfigRuntime.onPlayerDisconnect(player);
			}
		});
		if (dist.isClient()) {
			ConfigNeoForgeClient.register();
		} else {
			ServerConfigPluginLoader.createServerConfigManager(
				"MezzConfig Server File Watcher",
				FMLPaths.CONFIGDIR.get(),
				ConfigNeoForgePluginFinder.getServerPlugins()
			);
		}
	}
}
