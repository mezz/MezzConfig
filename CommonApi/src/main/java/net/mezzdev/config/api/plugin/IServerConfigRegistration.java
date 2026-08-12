package net.mezzdev.config.api.plugin;

import net.mezzdev.config.api.files.IConfigManager;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registration for one server config plugin.
 * An instance is passed to {@link IServerConfigPlugin#registerServerConfigFiles(IServerConfigRegistration)}.
 *
 * @since 0.2.0
 */
@ApiStatus.NonExtendable
public interface IServerConfigRegistration {
	/**
	 * Create a server-authoritative config schema builder.
	 * <p>
	 * The server loads the effective values from the distributable default under the normal config directory and then
	 * from the active world's server config file. The world file takes precedence and is created when the world starts.
	 * Connected clients receive an in-memory authoritative snapshot; they do not read or write the server's file.
	 * <p>
	 * Config editors can use {@link IConfigSchema#canEdit()} and
	 * {@link IConfigSchema#requestBatchUpdate(java.util.function.Consumer)}. On a remote connection, an update is sent to
	 * the server and is applied only if the requesting player has the server's operator permission level. The server
	 * validates and persists accepted values, then synchronizes the result to every connected client.
	 *
	 * @param configFileName relative file name inside this plugin's server config directories
	 * @param localizationPath translation key prefix for the config file
	 *
	 * @since 0.2.0
	 */
	IConfigSchemaBuilder createServerSchemaBuilder(String configFileName, String localizationPath);

	/**
	 * Get the active config manager that receives schemas built through this registration.
	 *
	 * @since 0.2.0
	 */
	IConfigManager getConfigManager();
}
