package net.mezzdev.config.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.mezzdev.config.file.ConfigPluginLoader;
import net.mezzdev.config.files.IConfigFileManager;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric client entry point for the config mod.
 */
public final class ConfigFabricClient implements ClientModInitializer {
	@Nullable
	private static IConfigFileManager configFileManager;

	@Override
	public void onInitializeClient() {
		configFileManager = ConfigPluginLoader.createConfigManager("Mezz Config File Watcher", ConfigFabricPluginFinder.getPlugins());
	}
}
