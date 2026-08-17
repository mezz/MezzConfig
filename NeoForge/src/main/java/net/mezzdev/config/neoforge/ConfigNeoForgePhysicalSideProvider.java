package net.mezzdev.config.neoforge;

import net.mezzdev.config.registration.ConfigPhysicalSideProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class ConfigNeoForgePhysicalSideProvider implements ConfigPhysicalSideProvider {
	@Override
	public boolean isPhysicalClient() {
		return FMLEnvironment.dist == Dist.CLIENT;
	}
}
