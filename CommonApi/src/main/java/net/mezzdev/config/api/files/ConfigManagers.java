package net.mezzdev.config.api.files;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Access to the active config manager created by MezzConfig.
 *
 * @since 0.1.0
 */
public final class ConfigManagers {
	private static @Nullable IConfigManager configManager;

	private ConfigManagers() {

	}

	/**
	 * Get the active config manager, if MezzConfig has loaded registered config plugins.
	 *
	 * @since 0.1.0
	 */
	public static Optional<IConfigManager> getConfigManager() {
		return Optional.ofNullable(configManager);
	}

	/**
	 * Set the active config manager.
	 *
	 * @since 0.1.0
	 */
	@ApiStatus.Internal
	public static void setConfigManager(IConfigManager configManager) {
		ConfigManagers.configManager = configManager;
	}
}
