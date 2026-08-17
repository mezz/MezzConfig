package net.mezzdev.config.forge;

import net.mezzdev.config.registration.ConfigPhysicalSideProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

public final class ConfigForgePhysicalSideProvider implements ConfigPhysicalSideProvider {
	@Override
	public boolean isPhysicalClient() {
		return FMLEnvironment.dist == Dist.CLIENT;
	}
}
