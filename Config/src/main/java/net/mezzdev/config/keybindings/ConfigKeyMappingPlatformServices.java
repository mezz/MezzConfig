package net.mezzdev.config.keybindings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ServiceLoader;

final class ConfigKeyMappingPlatformServices {
	private static final Logger LOGGER = LogManager.getLogger();
	public static final IConfigKeyMappingPlatformHelper PLATFORM_HELPER = loadPlatformHelper();

	private ConfigKeyMappingPlatformServices() {

	}

	private static IConfigKeyMappingPlatformHelper loadPlatformHelper() {
		return ServiceLoader.load(IConfigKeyMappingPlatformHelper.class)
			.findFirst()
			.map(service -> {
				LOGGER.debug("Loaded {} for service {}", service, IConfigKeyMappingPlatformHelper.class);
				return service;
			})
			.orElseGet(VanillaConfigKeyMappingPlatformHelper::new);
	}
}
