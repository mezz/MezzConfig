package net.mezzdev.config.api;

import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.sorting.ISortingConfig;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import org.jetbrains.annotations.ApiStatus;

import java.nio.file.Path;
import java.util.Comparator;

/**
 * Creates config schemas and persistent sort orders for one mod.
 * <p>
 * Get an instance from {@link Configs#forMod(String)}. Use a client schema for preferences shared across worlds, a
 * client-per-world schema for preferences that should vary by world or multiplayer server, or a server schema for
 * world-owned settings shared with connected clients. Add categories and values to the returned builder, then call
 * {@link IConfigSchemaBuilder#build()}.
 * <p>
 * Sorting configs are for persistent user-defined ordering of values discovered at runtime.
 * Each schema and sorting config must use its own file; MezzConfig rejects duplicate paths before accessing them.
 *
 * @since 0.3.0
 */
@ApiStatus.NonExtendable
public interface IConfigRegistration {
	/**
	 * Create a client config shared across worlds and server connections.
	 * <p>
	 * MezzConfig stores it in the mod's conventional client config directory. The schema loads synchronously when built.
	 * On a dedicated server, the builder remains usable so common registration code can run, but the built schema is
	 * inactive, default-backed, and is not registered or connected to a file.
	 *
	 * @param configFileName relative file name inside the mod's client config directory
	 * @param localizationPath translation key prefix for the config file
	 * @return client-owned schema builder
	 *
	 * @since 0.3.0
	 */
	IConfigSchemaBuilder createClientSchemaBuilder(String configFileName, String localizationPath);

	/**
	 * Create a server-owned config stored with each world and synchronized to connected clients.
	 * <p>
	 * The server loads the distributable default from the conventional config directory and the authoritative values from
	 * the active world's server config directory. Connected clients receive the server's effective values in memory.
	 *
	 * @param configFileName relative file name inside the mod's server config directories
	 * @param localizationPath translation key prefix for the config file
	 * @return server-owned schema builder
	 *
	 * @since 0.3.0
	 */
	IConfigSchemaBuilder createServerSchemaBuilder(String configFileName, String localizationPath);

	/**
	 * Create a client config with separate values for each singleplayer world or multiplayer server.
	 * <p>
	 * Use this for client preferences that should follow the current world or server instead of the whole installation.
	 * On a dedicated server, the builder remains usable so common registration code can run, but the built schema is
	 * inactive, default-backed, and is not registered or connected to a file.
	 *
	 * @param configFileName relative file name inside the mod's client-world config directories
	 * @param localizationPath translation key prefix for the config file
	 * @return client-owned world schema builder
	 *
	 * @since 0.3.0
	 */
	IConfigSchemaBuilder createClientPerWorldSchemaBuilder(String configFileName, String localizationPath);

	/**
	 * Create a client config stored at an explicit file path.
	 * <p>
	 * Use this when the conventional client config directory is not appropriate. Otherwise, prefer
	 * {@link #createClientSchemaBuilder(String, String)}.
	 * The supplied path is the complete config file location; MezzConfig does not append the mod id, ownership, or file
	 * name. Relative paths are captured as normalized absolute paths when this method is called.
	 * On a dedicated server, the builder remains usable so common registration code can run, but the built schema is
	 * inactive, default-backed, and does not access the supplied location.
	 * Building the schema reads or creates the file synchronously and fails if the location is unavailable. Later edits
	 * update the in-memory values before a delayed save; a later filesystem failure does not roll back the edit or reach
	 * the original editing call.
	 *
	 * @param configFile complete path to the config file
	 * @param localizationPath translation key prefix for the config file
	 * @return client-owned schema builder
	 *
	 * @since 0.3.0
	 */
	IConfigSchemaBuilder createClientSchemaBuilderAtLocation(Path configFile, String localizationPath);

	/**
	 * Create a persistent user-defined order for strings discovered at runtime.
	 * On a dedicated server, the sort order remains in memory and does not access a file.
	 *
	 * @param configFileName relative file name inside the mod's client config directory
	 * @param defaultSortOrder default order for values that are not in the file yet
	 * @param allowsRemovingValues whether values may be removed from this sort order
	 * @return the created sort order
	 * @throws IllegalArgumentException when the sort order's file path is already reserved
	 *
	 * @since 0.3.0
	 */
	ISortingConfig<String> createSortingConfig(
		String configFileName,
		Comparator<String> defaultSortOrder,
		boolean allowsRemovingValues
	);

	/**
	 * Create a persistent user-defined order for mod-specific values discovered at runtime.
	 * On a dedicated server, the sort order remains in memory and does not access a file.
	 * Every sortable value must be valid for the serializer and round-trip to an equal value without diagnostics. The
	 * serialized text is its persistent identity, so equal values must serialize identically and unequal values must not
	 * share serialized text.
	 *
	 * @param configFileName relative file name inside the mod's client config directory
	 * @param serializer serializer defining validation and persistent identities for sortable values
	 * @param defaultSortOrder default order for values that are not in the file yet
	 * @param allowsRemovingValues whether values may be removed from this sort order
	 * @param <T> effectively immutable sortable value type
	 * @return the created sort order
	 * @throws IllegalArgumentException when the sort order's file path is already reserved
	 *
	 * @since 0.3.0
	 */
	<T> ISortingConfig<T> createSortingConfig(
		String configFileName,
		IConfigValueSerializer<T> serializer,
		Comparator<T> defaultSortOrder,
		boolean allowsRemovingValues
	);
}
