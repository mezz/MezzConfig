package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.migration.IConfigMigrator;
import org.jetbrains.annotations.ApiStatus;

import java.nio.file.Path;
import java.util.List;

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
	 * Register a one-time migration from the first existing file in an ordered list of legacy locations.
	 * <p>
	 * Relative paths are captured as normalized absolute paths when this method is called. When this schema is built with
	 * an active local destination, MezzConfig attempts migration only if that destination is absent. The migrator runs
	 * after every value declared on this builder has been built and may queue typed value and sorting updates. MezzConfig
	 * backs up the selected legacy file, validates the complete transaction, and synchronously persists it in MezzConfig's
	 * current formats. The legacy source is never modified or removed.
	 * <p>
	 * The migrator receives the final structured outcome through
	 * {@link IConfigMigrator#onMigrationComplete(net.mezzdev.config.api.migration.IConfigMigrationResult)} before
	 * {@link #build()} returns. If migration fails, no queued update is applied and the missing schema destination is not
	 * created automatically, allowing another attempt on the next launch unless a later config edit creates it.
	 *
	 * @param legacyPaths ordered candidate legacy file paths; must not be empty
	 * @param migrator callback that parses the selected legacy file and queues typed updates
	 * @return this schema builder
	 * @throws IllegalArgumentException if the path list is empty, contains null or duplicate normalized paths, or a path
	 * cannot be converted to an absolute path
	 * @throws IllegalStateException if a migration was already registered or this builder was already built
	 *
	 * @since 0.3.0
	 */
	IConfigSchemaBuilder setLegacyMigration(List<Path> legacyPaths, IConfigMigrator migrator);

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
	 * @throws IllegalArgumentException when a backing file path is already reserved or the complete default config cannot
	 * be safely serialized
	 * @throws IllegalStateException when the schema has no storage category, a storage category has no config value, or a
	 * value builder has not been built
	 * @throws java.io.UncheckedIOException when an active backing file cannot initially be read or created
	 *
	 * @since 0.1.0
	 */
	IConfigSchema build();
}
