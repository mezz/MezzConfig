package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.IConfigRegistration;
import org.jetbrains.annotations.ApiStatus;

/**
 * Builds one config schema.
 * <p>
 * Create a builder with {@link IConfigRegistration#createClientSchemaBuilder(String, String)} or
 * {@link IConfigRegistration#createServerSchemaBuilder(String, String)}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigSchemaBuilder {
	/**
	 * Set the context that selects this schema's backing file.
	 * Builders use {@link ConfigScope#INSTALLATION} by default.
	 *
	 * @param scope config scope
	 * @return this builder
	 *
	 * @since 0.3.0
	 */
	IConfigSchemaBuilder setScope(ConfigScope scope);

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
	 * Build client and server schemas from the mod's primary initializer or constructor. On a dedicated server, client
	 * schemas are built as inactive, default-backed objects and are not registered. Register schemas before client
	 * config-screen setup when using automatically generated config screens. MezzConfigGUI's Forge and NeoForge
	 * config-screen factories include the schemas registered when client setup runs.
	 *
	 * @return the registered config schema
	 * @throws IllegalArgumentException when a backing file path is already reserved
	 *
	 * @since 0.1.0
	 */
	IConfigSchema build();
}
