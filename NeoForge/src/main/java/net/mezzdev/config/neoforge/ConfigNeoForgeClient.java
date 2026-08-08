package net.mezzdev.config.neoforge;

import net.mezzdev.config.plugin.ConfigPluginLoader;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ConfigNeoForgeClient {
	private ConfigNeoForgeClient() {

	}

	public static void register() {
		Path configRootDir = FMLPaths.CONFIGDIR.get();
		ConfigPluginLoader.createConfigManager(
			"MezzConfig File Watcher",
			configRootDir,
			!FMLLoader.isProduction(),
			ConfigNeoForgePluginFinder.getPlugins()
		);
	}
}
