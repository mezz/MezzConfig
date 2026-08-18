package net.mezzdev.config.api.internal;

import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.IConfigManager;
import net.mezzdev.config.api.IConfigRegistration;

import java.nio.file.Path;

/**
 * Runtime provider behind {@link Configs}.
 *
 * @since 0.3.0
 */
public interface IConfigProvider {
	IConfigRegistration createRegistration(Path configRootDir, String modId);

	IConfigManager getConfigManager();
}
