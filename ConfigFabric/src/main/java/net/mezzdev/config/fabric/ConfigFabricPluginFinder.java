package net.mezzdev.config.fabric;

import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.mezzdev.config.api.plugin.IConfigPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

public final class ConfigFabricPluginFinder {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String ENTRYPOINT_KEY = "mezz_config_plugin";

	private ConfigFabricPluginFinder() {

	}

	public static List<IConfigPlugin> getPlugins() {
		FabricLoader fabricLoader = FabricLoader.getInstance();
		List<EntrypointContainer<IConfigPlugin>> pluginContainers = fabricLoader.getEntrypointContainers(ENTRYPOINT_KEY, IConfigPlugin.class);
		return pluginContainers.stream()
			.<IConfigPlugin>mapMulti((entrypointContainer, consumer) -> {
				try {
					IConfigPlugin entrypoint = entrypointContainer.getEntrypoint();
					consumer.accept(entrypoint);
				} catch (EntrypointException e) {
					String modName = getModName(entrypointContainer);
					LOGGER.error("{} specified an invalid entrypoint for its config plugin", modName, e);
				} catch (RuntimeException | LinkageError e) {
					String modName = getModName(entrypointContainer);
					LOGGER.error("{} specified a broken entrypoint for its config plugin", modName, e);
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
