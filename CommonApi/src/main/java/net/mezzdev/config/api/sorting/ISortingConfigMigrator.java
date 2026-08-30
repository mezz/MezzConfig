package net.mezzdev.config.api.sorting;

import net.mezzdev.config.api.migration.IConfigMigrationResult;

import java.nio.file.Path;

/**
 * Imports a saved order from the format a mod used before adopting a MezzConfig sorting config.
 * <p>
 * Register a migrator with {@link ISortingConfig#setLegacyMigration}. Parse the old file in {@link #migrate} and pass the
 * converted order to the supplied context. MezzConfig handles backup, validation, and writing its current sorting format.
 *
 * @param <T> sortable value type
 *
 * @since 0.3.0
 */
@FunctionalInterface
public interface ISortingConfigMigrator<T> {
	/**
	 * Import the order from the selected legacy file.
	 *
	 * @param legacyPath normalized absolute path to the selected legacy file
	 * @param context context for supplying the converted saved order
	 * @throws Exception if the legacy file cannot be read or migrated
	 *
	 * @since 0.3.0
	 */
	void migrate(Path legacyPath, ISortingConfigMigrationContext<T> context) throws Exception;

	/**
	 * Receive the final migration outcome for logging, diagnostics, or user-facing feedback.
	 * <p>
	 * Override this when the mod needs to handle skipped migrations or failures that happen after {@link #migrate} returns.
	 * Lambdas may ignore it. An exception from this method is logged and does not change the completed outcome.
	 *
	 * @param result final structured migration result
	 *
	 * @since 0.3.0
	 */
	default void onMigrationComplete(IConfigMigrationResult result) {}
}
