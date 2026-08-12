package net.mezzdev.config.api.plugin;

/**
 * The common-side entry point for a mod's server-authoritative config registration.
 * <p>
 * Server config plugins are loaded on dedicated servers as well as clients, so implementations and every class they
 * reference must be safe to load without client-only Minecraft classes.
 * <p>
 * A physical client may invoke registration separately for its synchronized client view and integrated-server
 * authoritative state. Registration must therefore be repeatable and should only declare schemas, without one-time
 * side effects or guards that skip a later invocation.
 * <p>
 * Forge and NeoForge plugins are discovered by scanning for {@link ServerConfigPlugin}. Fabric plugins are discovered
 * from the {@code mezz_config_server_plugin} entrypoint in {@code fabric.mod.json}. A shared plugin class can support
 * all loaders by using the annotation and registering the same class as the Fabric entrypoint.
 *
 * @since 0.2.0
 */
public interface IServerConfigPlugin {
	/**
	 * The mod id that owns these server config schemas.
	 *
	 * @since 0.2.0
	 */
	String getModId();

	/**
	 * Register server-authoritative config schemas for this mod.
	 *
	 * @since 0.2.0
	 */
	void registerServerConfigFiles(IServerConfigRegistration registration);
}
