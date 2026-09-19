package net.mezzdev.config.forge;

import net.mezzdev.config.minecraft.MinecraftConfigEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ConfigForgePhysicalSideProvider extends MinecraftConfigEnvironment {
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
