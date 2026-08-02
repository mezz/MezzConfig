package net.mezzdev.config.forge;

import net.mezzdev.config.plugin.ConfigPluginLoader;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ConfigForgeClient {
	private ConfigForgeClient() {

	}

	public static void register() {
		Path configRootDir = FMLPaths.CONFIGDIR.get();
		ConfigPluginLoader.createConfigManager("MezzConfig File Watcher", configRootDir, ConfigForgePluginFinder.getPlugins());
	}
}
