package net.mezzdev.config.registration;

import java.nio.file.Path;

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
}
