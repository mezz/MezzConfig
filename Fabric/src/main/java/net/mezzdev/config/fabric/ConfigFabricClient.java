package net.mezzdev.config.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.mezzdev.config.plugin.ConfigPluginLoader;

import java.nio.file.Path;

/**
 * Fabric client entry point for the config mod.
 */
public final class ConfigFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Path configRootDir = FabricLoader.getInstance()
			.getConfigDir();
		ConfigPluginLoader.createConfigManager("MezzConfig File Watcher", configRootDir, ConfigFabricPluginFinder.getPlugins());
	}
}
