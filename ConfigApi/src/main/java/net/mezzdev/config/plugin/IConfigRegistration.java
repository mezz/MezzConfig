package net.mezzdev.config.plugin;

import net.mezzdev.config.files.IConfigFileManager;
import net.mezzdev.config.files.IConfigSaveScheduler;
import net.mezzdev.config.schema.IConfigSchema;
import net.mezzdev.config.schema.IConfigSchemaBuilder;

import java.nio.file.Path;

/**
 * Registration for config files provided by one config plugin.
 *
 * @since 19.39.0
 */
public interface IConfigRegistration {
	/**
	 * Create a config schema builder.
	 *
	 * @param configFile path to the config file
	 * @param localizationPath translation key prefix for the config file
	 * @param scheduler scheduler used for delayed saves
	 *
	 * @since 19.39.0
	 */
	IConfigSchemaBuilder createSchemaBuilder(Path configFile, String localizationPath, IConfigSaveScheduler scheduler);

	/**
	 * Register a built config file schema.
	 *
	 * @param configFile config file schema to manage
	 *
	 * @since 19.39.0
	 */
	void registerConfigFile(IConfigSchema configFile);

	/**
	 * Get the config manager that is receiving registered files.
	 *
	 * @since 19.39.0
	 */
	IConfigFileManager getConfigManager();
}
