package net.mezzdev.config.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.mezzdev.config.plugin.ServerConfigPluginLoader;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge entry point for the config mod.
 */
@Mod(ConfigForge.MOD_ID)
public final class ConfigForge {
	public static final String MOD_ID = "mezz_config";

	public ConfigForge() {
		ConfigForgeNetwork network = new ConfigForgeNetwork();
		MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> ServerConfigRuntime.onServerStarted(event.getServer()));
		MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> ServerConfigRuntime.onServerStopped());
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				ServerConfigRuntime.onServerTick();
			}
		});
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				ServerConfigRuntime.onPlayerJoin(player);
			}
		});
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				ServerConfigRuntime.onPlayerDisconnect(player);
			}
		});
		ConfigForgeClientSafeRunner clientSafeRunner = new ConfigForgeClientSafeRunner(network);
		DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> clientSafeRunner::registerClient);
		if (FMLLoader.getDist() == Dist.DEDICATED_SERVER) {
			ServerConfigPluginLoader.createServerConfigManager(
				"MezzConfig Server File Watcher",
				FMLPaths.CONFIGDIR.get(),
				ConfigForgePluginFinder.getServerPlugins()
			);
		}
	}
}
