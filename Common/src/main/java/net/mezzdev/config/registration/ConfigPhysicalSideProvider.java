package net.mezzdev.config.registration;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Supplies loader-specific environment details directly from the active mod loader.
 */
public interface ConfigPhysicalSideProvider {
	/**
	 * Return the active mod loader's conventional config directory.
	 */
	Path getConfigRoot();

	/**
	 * Return whether Minecraft is running in a physical client process.
	 */
	boolean isPhysicalClient();

	/**
	 * Return whether Minecraft is running from a development environment.
	 */
	boolean isDevelopmentEnvironment();

	Optional<Path> getClientWorldPath(Path configDirectory);

	boolean hasTranslation(String key);

	String translate(String key, Object... arguments);
}
