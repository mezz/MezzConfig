package net.mezzdev.config.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge entry point for the config mod.
 */
@Mod(ConfigNeoForge.MOD_ID)
public final class ConfigNeoForge {
	public static final String MOD_ID = "mezz_config";

	public ConfigNeoForge(Dist dist) {
		if (dist.isClient()) {
			ConfigNeoForgeClient.register();
		}
	}
}
