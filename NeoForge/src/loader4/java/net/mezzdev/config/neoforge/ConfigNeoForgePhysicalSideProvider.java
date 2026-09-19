package net.mezzdev.config.neoforge;

import net.mezzdev.config.minecraft.MinecraftConfigEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ConfigNeoForgePhysicalSideProvider extends MinecraftConfigEnvironment {
	@Override
	public Path getConfigRoot() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public boolean isPhysicalClient() {
		return FMLEnvironment.dist == Dist.CLIENT;
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return !FMLLoader.isProduction();
	}
}
