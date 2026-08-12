package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.plugin.IConfigRegistration;
import org.jetbrains.annotations.ApiStatus;

/**
 * Builds one config schema.
 * <p>
 * Create a builder for your schema with {@link IConfigRegistration#createSchemaBuilder(String, String)},
 * {@link IConfigRegistration#createClientWorldSchemaBuilder(String, String)}, or
 * {@link net.mezzdev.config.api.plugin.IServerConfigRegistration#createServerSchemaBuilder(String, String)}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigSchemaBuilder {
	/**
	 * Add a storage category to this config schema.
	 * Categories are returned from {@link IConfigSchema#getCategories()} in the order they are added here.
	 *
	 * @param name stable storage name for the category
	 *
	 * @since 0.1.0
	 */
	IConfigCategoryBuilder addCategory(String name);

	/**
	 * Add a category for config editors without adding a category to the config file.
	 * Editor categories are returned from {@link IConfigSchema#getEditorCategories()} in the order they are added here.
	 *
	 * @param name stable editor category name
	 *
	 * @since 0.1.0
	 */
	IConfigEditorCategoryBuilder addEditorCategory(String name);

	/**
	 * Build and register the config schema.
	 * A builder may only be built once.
	 *
	 * @since 0.1.0
	 */
	IConfigSchema build();
}
