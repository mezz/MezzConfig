package net.mezzdev.config.files;

import net.mezzdev.config.schema.IConfigSchema;

/**
 * Manages config files.
 *
 * @since 19.39.0
 */
public interface IConfigFileManager extends IConfigManager {
	/**
	 * Register a config file.
	 *
	 * @param configFile config file schema to manage
	 *
	 * @since 19.39.0
	 */
	void registerConfigFile(IConfigSchema configFile);

	/**
	 * Start watching registered config files for external changes.
	 *
	 * @since 19.39.0
	 */
	void startWatching();

	/**
	 * Save all registered config files.
	 *
	 * @since 19.39.0
	 */
	void saveAll();
}
