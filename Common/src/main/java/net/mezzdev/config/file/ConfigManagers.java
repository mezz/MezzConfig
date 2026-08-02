package net.mezzdev.config.file;

import net.mezzdev.config.api.files.IConfigManager;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Access to the active config manager created by the config mod.
 */
public final class ConfigManagers {
	@Nullable
	private static IConfigManager configManager;

	private ConfigManagers() {

	}

	public static Optional<IConfigManager> getConfigManager() {
		return Optional.ofNullable(configManager);
	}

	public static void setConfigManager(IConfigManager configManager) {
		ConfigManagers.configManager = configManager;
	}
}
