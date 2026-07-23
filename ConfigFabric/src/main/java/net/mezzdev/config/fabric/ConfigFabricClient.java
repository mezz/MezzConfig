package net.mezzdev.config.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.mezzdev.config.plugin.ConfigPluginLoader;
import net.mezzdev.config.api.files.IConfigManager;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Fabric client entry point for the config mod.
 */
public final class ConfigFabricClient implements ClientModInitializer {
	@Nullable
	private static IConfigManager configManager;

	@Override
	public void onInitializeClient() {
		Path configRootDir = FabricLoader.getInstance()
			.getConfigDir();
		configManager = ConfigPluginLoader.createConfigManager("Mezz Config File Watcher", configRootDir, ConfigFabricPluginFinder.getPlugins());
	}
}
