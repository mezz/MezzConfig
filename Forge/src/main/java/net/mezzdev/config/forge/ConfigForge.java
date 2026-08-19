package net.mezzdev.config.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge entry point for the config mod.
 */
@Mod(ConfigForge.MOD_ID)
public final class ConfigForge {
	public static final String MOD_ID = "mezz_config";

	public ConfigForge() {
		new ConfigForgeNetwork();
		MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> ServerConfigRuntime.onServerStarted(event.getServer()));
		MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> ServerConfigRuntime.onServerStopped());
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				ServerConfigRuntime.onPlayerJoin(player);
			}
		});
		ConfigForgeClientSafeRunner clientSafeRunner = new ConfigForgeClientSafeRunner();
		DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> clientSafeRunner::registerClient);
	}
}
