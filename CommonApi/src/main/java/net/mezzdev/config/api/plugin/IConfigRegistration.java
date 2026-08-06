package net.mezzdev.config.api.plugin;

import net.mezzdev.config.api.files.IConfigManager;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.sorting.ISortingConfig;
import org.jetbrains.annotations.ApiStatus;

import java.util.Comparator;

/**
 * Registration for one config plugin.
 * Use this to create config schemas and sort orders in your plugin's config directory.
 * <p>
 * An instance is passed to your plugin here: {@link IConfigPlugin#registerConfigFiles(IConfigRegistration)}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigRegistration {
	/**
	 * Create a config schema builder.
	 * The file is resolved inside the config directory owned by this plugin's mod id.
	 *
	 * @param configFileName relative file name for the config file
	 * @param localizationPath translation key prefix for the config file
	 *
	 * @since 0.1.0
	 */
	IConfigSchemaBuilder createSchemaBuilder(String configFileName, String localizationPath);

	/**
	 * Create a client-world config schema builder.
	 * <p>
	 * The file is resolved inside a client-side world or server-specific directory owned by this plugin's mod id.
	 * These schemas are only active while the client is connected to a world or server.
	 *
	 * @param configFileName relative file name for the config file
	 * @param localizationPath translation key prefix for the config file
	 *
	 * @since 0.1.0
	 */
	IConfigSchemaBuilder createClientWorldSchemaBuilder(String configFileName, String localizationPath);

	/**
	 * Create a string-backed sort order.
	 * The file is resolved inside the config directory owned by this plugin's mod id.
	 * When removal is allowed, saved sort-order files may omit values.
	 * When removal is not allowed, missing values are appended using the default sort order.
	 *
	 * @param configFileName relative file name for the sort order file
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
	 * Get the config manager that receives registered schemas.
	 *
	 * @since 0.1.0
	 */
	IConfigManager getConfigManager();
}
