package net.mezzdev.config.neoforge;

import net.mezzdev.config.file.ConfigPluginLoader;
import net.mezzdev.config.files.IConfigManager;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public final class ConfigNeoForgeClient {
	@Nullable
	private static IConfigManager configManager;

	private ConfigNeoForgeClient() {

	}

	public static void register() {
		Path configRootDir = FMLPaths.CONFIGDIR.get();
		configManager = ConfigPluginLoader.createConfigManager("Mezz Config File Watcher", configRootDir, ConfigNeoForgePluginFinder.getPlugins());
	}
}
