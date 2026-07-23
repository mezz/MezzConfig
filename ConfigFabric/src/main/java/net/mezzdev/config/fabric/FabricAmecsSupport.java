package net.mezzdev.config.fabric;

import net.fabricmc.loader.api.FabricLoader;

final class FabricAmecsSupport {
	private static final String AMECS_MOD_ID = "amecsapi";
	private static final String DISABLE_AMECS_SUPPORT_PROPERTY = "mezz_config.fabric.disableAmecsSupport";
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded(AMECS_MOD_ID) &&
		!Boolean.getBoolean(DISABLE_AMECS_SUPPORT_PROPERTY);

	private FabricAmecsSupport() {

	}

	public static boolean isEnabled() {
		return ENABLED;
	}
}
