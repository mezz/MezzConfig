package net.mezzdev.config.keybindings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ServiceLoader;
import java.util.function.Supplier;

final class ConfigKeyMappingPlatformServices {
	private static final Logger LOGGER = LogManager.getLogger();
	public static final IConfigKeyMappingPlatformHelper PLATFORM_HELPER =
		load(IConfigKeyMappingPlatformHelper.class, VanillaConfigKeyMappingPlatformHelper::new);

	private ConfigKeyMappingPlatformServices() {

	}

	private static <T> T load(Class<T> serviceClass, Supplier<T> fallback) {
		return ServiceLoader.load(serviceClass)
			.findFirst()
			.map(service -> {
				LOGGER.debug("Loaded {} for service {}", service, serviceClass);
				return service;
			})
			.orElseGet(() -> {
				T fallbackService = fallback.get();
				LOGGER.debug("Loaded {} for service {}", fallbackService, serviceClass);
				return fallbackService;
			});
	}
}
