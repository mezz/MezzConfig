package net.mezzdev.config.api.plugin;

import net.mezzdev.config.api.files.IConfigManager;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.sorting.ISortingConfig;
import org.jetbrains.annotations.ApiStatus;

import java.util.Comparator;

/**
 * Registration for one config plugin.
 * Use this to create config schemas and sort orders in your plugin's config directory. Use
 * {@link #createSchemaBuilder(String, String)} for preferences that apply everywhere, or
 * {@link #createClientWorldSchemaBuilder(String, String)} when the player should have separate client-side preferences
 * for each singleplayer world and multiplayer server.
 * <p>
 * An instance is passed to your plugin here: {@link IConfigPlugin#registerConfigFiles(IConfigRegistration)}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigRegistration {
	/**
	 * Create an always-active client config schema builder.
	 * <p>
	 * Use this for client-owned preferences that should remain the same when the player moves between singleplayer worlds
	 * and multiplayer servers. For preferences that should vary with the current world or server, use
	 * {@link #createClientWorldSchemaBuilder(String, String)} instead.
	 * <p>
	 * The registered file inside this plugin's config directory is the distributable default. MezzConfig loads declared
	 * code defaults first, then this file, then the player's profile-specific file. The player file is created only after
	 * the player changes a value and takes precedence over the distributable default from then on.
	 *
	 * @param configFileName relative file name inside this plugin's config directory
	 * @param localizationPath translation key prefix for the config file
	 *
	 * @since 0.1.0
	 */
	IConfigSchemaBuilder createSchemaBuilder(String configFileName, String localizationPath);

	/**
	 * Create a context-specific client config schema builder with separate values for each singleplayer world or
	 * multiplayer server.
	 * <p>
	 * Use this for client-owned preferences whose appropriate value depends on the current world or connection, such as a
	 * world-specific overlay layout or a different client-side filter for each server. This is not server configuration:
	 * the server does not load, control, or synchronize these values, so they must not be used for authoritative gameplay
	 * behavior.
	 * <p>
	 * Unlike a schema from {@link #createSchemaBuilder(String, String)}, this schema is inactive while the client is not
	 * connected to a world or server. While inactive, {@link IConfigSchema#getPath()} is empty, values read as their
	 * declared defaults, and updates are rejected.
	 * <p>
	 * The registered file inside this plugin's default client-world directory is the distributable default for every
	 * context. MezzConfig loads declared code defaults first, then this file, then the player's file for the active world
	 * or server. Player changes in one context do not affect any other world or server.
	 *
	 * @param configFileName relative file name inside the client-world config directories
	 * @param localizationPath translation key prefix for the config file
	 *
	 * @since 0.1.0
	 */
	IConfigSchemaBuilder createClientWorldSchemaBuilder(String configFileName, String localizationPath);

	/**
	 * Create an always-active, string-backed client sort order.
	 * <p>
	 * The registered file inside this plugin's config directory is the distributable default. Player changes are stored
	 * in a profile-specific file and take precedence over that default.
	 * <p>
	 * When removal is allowed, saved sort-order files may omit values.
	 * When removal is not allowed, missing values are appended using the default sort order.
	 *
	 * @param configFileName relative file name inside this plugin's config directory
	 * @param defaultSortOrder default order for values that are not in the file yet
	 * @param allowsRemovingValues whether values may be removed from this sort order
	 * @return the created sort order
	 *
	 * @since 0.1.0
	 */
	ISortingConfig<String> createSortingConfig(
		String configFileName,
		Comparator<String> defaultSortOrder,
		boolean allowsRemovingValues
	);

	/**
	 * Get the active config manager that receives schemas built through this registration. On a client, it also contains
	 * registered server schemas so config editors can display synchronized server settings.
	 *
	 * @since 0.1.0
	 */
	IConfigManager getConfigManager();
}
