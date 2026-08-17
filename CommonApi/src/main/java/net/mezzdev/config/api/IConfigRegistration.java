package net.mezzdev.config.api;

import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.sorting.ISortingConfig;
import org.jetbrains.annotations.ApiStatus;

import java.util.Comparator;

/**
 * Creates config schemas and sort orders owned by one mod.
 *
 * @since 0.3.0
 */
@ApiStatus.NonExtendable
public interface IConfigRegistration {
	/**
	 * Create a client-owned config schema builder with installation scope.
	 * On a dedicated server, the builder remains usable so common registration code can run, but the built schema is
	 * inactive, default-backed, and is not registered or connected to a file.
	 * The schema loads synchronously when built. Use
	 * {@link IConfigSchemaBuilder#setScope(net.mezzdev.config.api.schema.ConfigScope)} for world-specific values.
	 *
	 * @param configFileName relative file name inside the mod's client config directory
	 * @param localizationPath translation key prefix for the config file
	 * @return client-owned schema builder
	 *
	 * @since 0.3.0
	 */
	IConfigSchemaBuilder createClientSchemaBuilder(String configFileName, String localizationPath);

	/**
	 * Create a server-owned config schema builder with installation scope.
	 * The schema loads synchronously when built. World-scoped server schemas are authoritative for the active world and
	 * synchronized to connected clients.
	 *
	 * @param configFileName relative file name inside the mod's server config directory
	 * @param localizationPath translation key prefix for the config file
	 * @return server-owned schema builder
	 *
	 * @since 0.3.0
	 */
	IConfigSchemaBuilder createServerSchemaBuilder(String configFileName, String localizationPath);

	/**
	 * Create an installation-scoped, string-backed client sort order.
	 * On a dedicated server, the sort order remains in memory and does not access a file.
	 *
	 * @param configFileName relative file name inside the mod's client config directory
	 * @param defaultSortOrder default order for values that are not in the file yet
	 * @param allowsRemovingValues whether values may be removed from this sort order
	 * @return the created sort order
	 *
	 * @since 0.3.0
	 */
	ISortingConfig<String> createSortingConfig(
		String configFileName,
		Comparator<String> defaultSortOrder,
		boolean allowsRemovingValues
	);
}
