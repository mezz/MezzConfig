package net.mezzdev.config.neoforge;

import net.mezzdev.config.registration.ConfigPhysicalSideProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ConfigNeoForgePhysicalSideProvider implements ConfigPhysicalSideProvider {
	@Override
	public Path getConfigRoot() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public boolean isPhysicalClient() {
		return FMLEnvironment.dist == Dist.CLIENT;
	}
}
