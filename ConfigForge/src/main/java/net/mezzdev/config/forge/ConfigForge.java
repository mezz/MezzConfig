package net.mezzdev.config.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge entry point for the config mod.
 */
@Mod(ConfigForge.MOD_ID)
public final class ConfigForge {
	public static final String MOD_ID = "mezz_config";

	public ConfigForge() {
		ConfigForgeClientSafeRunner clientSafeRunner = new ConfigForgeClientSafeRunner();
		DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> clientSafeRunner::registerClient);
	}
}
