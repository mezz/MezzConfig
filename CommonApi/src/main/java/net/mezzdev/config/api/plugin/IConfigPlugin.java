package net.mezzdev.config.api.plugin;

/**
 * The main class to implement for a mod's config registration.
 * Multiple config plugins may return the same mod id to contribute separate schemas and sort orders
 * under the same mod-owned config directory.
 * <p>
 * Forge and NeoForge plugins are discovered by scanning for the {@link ConfigPlugin} annotation.
 * Annotate your plugin class and provide a public constructor with no arguments.
 * <p>
 * Fabric plugins are discovered from {@code fabric.mod.json}. Add your plugin class to the
 * {@code mezz_config_plugin} entrypoint.
 * <p>
 * A shared plugin class can support all loaders by using the annotation and registering the same class as the
 * Fabric entrypoint.
 *
 * @since 0.1.0
 */
public interface IConfigPlugin {
	/**
	 * The mod id that owns these config schemas and sort orders.
	 *
	 * @since 0.1.0
	 */
	String getModId();

	/**
	 * Register config schemas and sort orders for this mod.
	 *
	 * @since 0.1.0
	 */
	void registerConfigFiles(IConfigRegistration registration);
}
