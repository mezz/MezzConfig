package net.mezzdev.config.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.mezzdev.config.registration.ConfigPhysicalSideProvider;

public final class ConfigFabricPhysicalSideProvider implements ConfigPhysicalSideProvider {
	@Override
	public boolean isPhysicalClient() {
		return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
	}
}
