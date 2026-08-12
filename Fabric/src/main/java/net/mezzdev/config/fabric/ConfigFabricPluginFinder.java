package net.mezzdev.config.fabric;

import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.mezzdev.config.api.plugin.IConfigPlugin;
import net.mezzdev.config.api.plugin.IServerConfigPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

public final class ConfigFabricPluginFinder {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String CLIENT_ENTRYPOINT_KEY = "mezz_config_plugin";
	private static final String SERVER_ENTRYPOINT_KEY = "mezz_config_server_plugin";

	private ConfigFabricPluginFinder() {

	}

	public static List<IConfigPlugin> getPlugins() {
		return getPlugins(CLIENT_ENTRYPOINT_KEY, IConfigPlugin.class, "config plugin");
	}

	public static List<IServerConfigPlugin> getServerPlugins() {
		return getPlugins(SERVER_ENTRYPOINT_KEY, IServerConfigPlugin.class, "server config plugin");
	}

	private static <T> List<T> getPlugins(String entrypointKey, Class<T> pluginClass, String pluginName) {
		FabricLoader fabricLoader = FabricLoader.getInstance();
		List<EntrypointContainer<T>> pluginContainers = fabricLoader.getEntrypointContainers(entrypointKey, pluginClass);
		return pluginContainers.stream()
			.<T>mapMulti((entrypointContainer, consumer) -> {
				try {
					T entrypoint = entrypointContainer.getEntrypoint();
					consumer.accept(entrypoint);
				} catch (EntrypointException e) {
					String modName = getModName(entrypointContainer);
					LOGGER.error("{} specified an invalid entrypoint for its {}", modName, pluginName, e);
				} catch (RuntimeException | LinkageError e) {
					String modName = getModName(entrypointContainer);
					LOGGER.error("{} specified a broken entrypoint for its {}", modName, pluginName, e);
				}
			})
			.collect(Collectors.toList());
	}

	private static String getModName(EntrypointContainer<?> entrypointContainer) {
		try {
			ModContainer provider = entrypointContainer.getProvider();
			ModMetadata metadata = provider.getMetadata();
			return metadata.getName();
		} catch (RuntimeException | LinkageError ignored) {
			return "unknown";
		}
	}
}
