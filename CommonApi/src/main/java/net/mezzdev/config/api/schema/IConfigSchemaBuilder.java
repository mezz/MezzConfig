package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.IConfigRegistration;
import org.jetbrains.annotations.ApiStatus;

/**
 * Builds one config schema.
 * <p>
 * Create a builder with one of the schema factory methods on {@link IConfigRegistration}. Each factory selects a
 * complete supported schema type and storage location before returning the builder.
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
	 * Build client and server schemas from the mod's primary initializer or constructor. On a dedicated server, client
	 * schemas are built as inactive, default-backed objects and are not registered. Register schemas before client
	 * config-screen setup when using automatically generated config screens. MezzConfigGUI's Forge and NeoForge
	 * config-screen factories include the schemas registered when client setup runs.
	 * For a currently active file-backed schema, the initial file load or creation is synchronous.
	 *
	 * @return the registered config schema
	 * @throws IllegalArgumentException when a backing file path is already reserved
	 * @throws java.io.UncheckedIOException when an active backing file cannot initially be read or created
	 *
	 * @since 0.1.0
	 */
	IConfigSchema build();
}
