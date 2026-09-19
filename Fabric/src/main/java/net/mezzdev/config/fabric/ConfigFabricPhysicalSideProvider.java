package net.mezzdev.config.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.mezzdev.config.minecraft.MinecraftConfigEnvironment;

import java.nio.file.Path;

public final class ConfigFabricPhysicalSideProvider extends MinecraftConfigEnvironment {
	@Override
	public Path getConfigRoot() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean isPhysicalClient() {
		return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}
}
