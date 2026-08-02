package net.mezzdev.config.forge;

import net.mezzdev.config.plugin.ConfigPluginLoader;
import net.mezzdev.config.api.files.IConfigManager;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public final class ConfigForgeClient {
	@Nullable
	private static IConfigManager configManager;

	private ConfigForgeClient() {

	}

	public static void register() {
		Path configRootDir = FMLPaths.CONFIGDIR.get();
		configManager = ConfigPluginLoader.createConfigManager("Mezz Config File Watcher", configRootDir, ConfigForgePluginFinder.getPlugins());
	}
}
