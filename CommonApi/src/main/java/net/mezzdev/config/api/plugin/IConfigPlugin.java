package net.mezzdev.config.api.plugin;

/**
 * The main class to implement to register config files for a mod.
 * <p>
 * Forge and NeoForge plugins are discovered by scanning for the {@link ConfigPlugin} annotation.
 * Annotate your plugin class and provide a constructor with no arguments.
 * <p>
 * Fabric plugins are discovered from {@code fabric.mod.json}. Add your plugin class to the
 * {@code mezz_config_plugin} entrypoint.
 * <p>
 * A shared plugin class can support all loaders by using the annotation and registering the same class as the
 * Fabric entrypoint.
 *
 * @since 19.39.0
 */
public interface IConfigPlugin {
	/**
	 * The mod id that owns these config files.
	 *
	 * @since 19.39.0
	 */
	String getModId();

	/**
	 * Register config files for this mod.
	 *
	 * @since 19.39.0
	 */
	void registerConfigFiles(IConfigRegistration registration);
}
