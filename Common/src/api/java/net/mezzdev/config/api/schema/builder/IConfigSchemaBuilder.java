package net.mezzdev.config.api.schema.builder;

import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.migration.IConfigMigrator;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.value.builder.IConfigValueBuilder;
import org.jetbrains.annotations.ApiStatus;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Declares the settings stored in one config file or synchronized as one server config.
 * <p>
 * Obtain a builder from {@link IConfigRegistration}, add storage categories, build every value, then call {@link #build()}
 * during mod initialization. Add editor-only categories only when the config screen should organize values differently
 * from the file.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigSchemaBuilder {
	/**
	 * Add a named group of values to the config file.
	 * Categories are returned from {@link IConfigSchema#getCategories()} in the order they are added here.
	 *
	 * @param name stable storage name for the category
	 *
	 * @since 0.1.0
	 */
	IConfigCategoryBuilder addCategory(String name);

	/**
	 * Add a config-screen category without changing how values are grouped in the file.
	 * Editor categories are returned from {@link IConfigSchema#getEditorCategories()} in the order they are added here.
	 *
	 * @param name stable editor category name
	 *
	 * @since 0.1.0
	 */
	IConfigEditorCategoryBuilder addEditorCategory(String name);

	/**
	 * Load this schema from a MezzConfig file at an older location when the destination does not exist yet.
	 * <p>
	 * Use this when a mod moves or renames a MezzConfig file. Values are matched by their current storage names and by the
	 * legacy names and migrations declared on {@link IConfigValueBuilder}. The first existing source is preserved and backed
	 * up, and MezzConfig writes the imported values to the new location in its current format.
	 * <p>
	 * To import a file that was not written by MezzConfig, use {@link #setLegacyMigration(List, IConfigMigrator)} instead.
	 *
	 * @param legacyPaths ordered candidate MezzConfig source paths; must not be empty
	 * @return this schema builder
	 * @throws IllegalArgumentException if the path list is empty, contains null or duplicate normalized paths, or a path
	 * cannot be converted to an absolute path
	 * @throws IllegalStateException if a legacy source or migration was already registered or this builder was already built
	 *
	 * @since 0.3.0
	 */
	IConfigSchemaBuilder setLegacySources(List<Path> legacyPaths);

	/**
	 * Import a config file that the mod used before adopting MezzConfig.
	 * <p>
	 * Use this when the old file was not written by MezzConfig. To move a MezzConfig file from another location, use
	 * {@link #setLegacySources(List)} and, when needed, declare renamed, moved, or converted values with the legacy methods
	 * on {@link IConfigValueBuilder}.
	 * <p>
	 * Declare and build the destination values first so the migrator can update them. The paths are checked in order, which
	 * supports mods that used more than one old location. Migration is considered only when the new config does not exist.
	 * <p>
	 * The migrator parses the old format and supplies typed values; MezzConfig preserves and backs up the source, validates
	 * the complete import, and writes the new format atomically. See {@link IConfigMigrator} for an example.
	 *
	 * @param legacyPaths ordered candidate legacy file paths; must not be empty
	 * @param migrator callback that parses the selected legacy file and queues typed updates
	 * @return this schema builder
	 * @throws IllegalArgumentException if the path list is empty, contains null or duplicate normalized paths, or a path
	 * cannot be converted to an absolute path
	 * @throws IllegalStateException if a legacy source or migration was already registered or this builder was already built
	 *
	 * @since 0.3.0
	 */
	IConfigSchemaBuilder setLegacyMigration(List<Path> legacyPaths, IConfigMigrator migrator);

	/**
	 * Finish the declaration, register the schema, and load its initial values.
	 * <p>
	 * Call this once during mod initialization, after every value builder has been built. Build before registering generated
	 * config screens so integrations can discover the schema. Client schemas built on a dedicated server remain inactive
	 * and default-backed, which allows the same declaration code to run on both sides. Active local files are loaded or
	 * created before this method returns.
	 *
	 * @return the registered config schema
	 * @throws IllegalArgumentException when a backing file path is already reserved or the complete default config cannot
	 * be safely serialized
	 * @throws IllegalStateException when the schema has no storage category, a storage category has no config value, or a
	 * value builder has not been built
	 * @throws UncheckedIOException when an active backing file cannot initially be read or created
	 *
	 * @since 0.1.0
	 */
	IConfigSchema build();
}
