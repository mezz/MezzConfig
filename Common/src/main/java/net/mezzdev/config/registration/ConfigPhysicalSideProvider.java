package net.mezzdev.config.registration;

/**
 * Supplies the physical process side directly from the active mod loader.
 */
public interface ConfigPhysicalSideProvider {
	/**
	 * Return whether Minecraft is running in a physical client process.
	 */
	boolean isPhysicalClient();
}
