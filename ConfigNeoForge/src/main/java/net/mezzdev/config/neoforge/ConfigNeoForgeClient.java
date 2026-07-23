package net.mezzdev.config.neoforge;

import net.mezzdev.config.file.ConfigPluginLoader;
import net.mezzdev.config.files.IConfigFileManager;
import org.jetbrains.annotations.Nullable;

public final class ConfigNeoForgeClient {
	@Nullable
	private static IConfigFileManager configFileManager;

	private ConfigNeoForgeClient() {

	}

	public static void register() {
		configFileManager = ConfigPluginLoader.createConfigManager("Mezz Config File Watcher", ConfigNeoForgePluginFinder.getPlugins());
	}
}
