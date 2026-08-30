package net.mezzdev.config.api.migration;

import java.nio.file.Path;

/**
 * Imports a config file from the format a mod used before adopting MezzConfig.
 * <p>
 * Register a migrator with
 * {@link net.mezzdev.config.api.schema.IConfigSchemaBuilder#setLegacyMigration(java.util.List, IConfigMigrator)}. Parse
 * the old file in {@link #migrate(Path, IConfigMigrationContext)} and pass converted values to the migration context.
 * MezzConfig handles backup, validation, and writing the new config, so migrators never need to understand MezzConfig's
 * file format.
 *
 * @since 0.3.0
 */
@FunctionalInterface
public interface IConfigMigrator {
	/**
	 * Import values from the selected legacy file.
	 * <p>
	 * Read the old format from {@code legacyPath} and call the context's typed update methods for every setting to import.
	 * Throw an exception when the old file cannot be interpreted; MezzConfig will leave the new config unchanged.
	 *
	 * @param legacyPath normalized absolute path to the selected legacy file
	 * @param context context for typed config value and sorting updates
	 * @throws Exception if the legacy file cannot be read or migrated
	 *
	 * @since 0.3.0
	 */
	void migrate(Path legacyPath, IConfigMigrationContext context) throws Exception;

	/**
	 * Receive the final migration outcome for logging, diagnostics, or user-facing feedback.
	 * <p>
	 * Override this when the mod needs to handle skipped migrations or failures that happen after {@link #migrate} returns.
	 * Lambdas may ignore it. MezzConfig calls it once for every registered migration after reaching a final outcome. An
	 * exception from this method is logged and does not change that outcome.
	 *
	 * @param result final structured migration result
	 *
	 * @since 0.3.0
	 */
	default void onMigrationComplete(IConfigMigrationResult result) {}
}
