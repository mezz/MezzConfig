package net.mezzdev.config.api.plugin;

import net.mezzdev.config.api.files.IConfigManager;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;

/**
 * Registration for config files provided by one config plugin.
 *
 * @since 19.39.0
 */
public interface IConfigRegistration {
	/**
	 * Create a config schema builder.
	 * The file is resolved inside the config directory owned by this plugin's mod id.
	 *
	 * @param configFileName relative file name for the config file
	 * @param localizationPath translation key prefix for the config file
	 *
	 * @since 19.39.0
	 */
	IConfigSchemaBuilder createSchemaBuilder(String configFileName, String localizationPath);

	/**
	 * Get the config manager that is receiving registered files.
	 *
	 * @since 19.39.0
	 */
	IConfigManager getConfigManager();
}
