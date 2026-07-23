package net.mezzdev.config.forge;

import net.mezzdev.config.file.ConfigPluginLoader;
import net.mezzdev.config.files.IConfigFileManager;
import org.jetbrains.annotations.Nullable;

public final class ConfigForgeClient {
	@Nullable
	private static IConfigFileManager configFileManager;

	private ConfigForgeClient() {

	}

	public static void register() {
		configFileManager = ConfigPluginLoader.createConfigManager("Mezz Config File Watcher", ConfigForgePluginFinder.getPlugins());
	}
}
