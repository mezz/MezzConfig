package net.mezzdev.config.api.migration;

import org.jetbrains.annotations.ApiStatus;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Reports the final outcome of a registered legacy migration.
 * <p>
 * Inspect this from {@link IConfigMigrator#onMigrationComplete(IConfigMigrationResult)} or
 * {@link ISortingConfigMigrator#onMigrationComplete(IConfigMigrationResult)} when the mod wants to log a failure, tell the
 * user what was imported, or locate the preserved backup. This also reports outcomes that the migration callback cannot
 * observe itself, such as a skipped migration or a failure after it returns.
 *
 * @since 0.3.0
 */
@ApiStatus.NonExtendable
public interface IConfigMigrationResult {
	/**
	 * Get the migration outcome.
	 *
	 * @return migration status
	 *
	 * @since 0.3.0
	 */
	ConfigMigrationStatus getStatus();

	/**
	 * Get the new MezzConfig file that migration targeted.
	 * This is empty when the migration target had no active local destination.
	 *
	 * @return migration destination, if one was available
	 *
	 * @since 0.3.0
	 */
	Optional<Path> getDestinationPath();

	/**
	 * Get the old config file that was selected for import.
	 *
	 * @return selected normalized absolute legacy path, if one was found
	 *
	 * @since 0.3.0
	 */
	Optional<Path> getLegacyPath();

	/**
	 * Get the preserved backup that can be shown to the user or used for recovery.
	 * A failed migration may still have a backup when failure happened after backup creation.
	 *
	 * @return normalized absolute backup path, if a backup was created
	 *
	 * @since 0.3.0
	 */
	Optional<Path> getBackupPath();

	/**
	 * Get the reason an attempted migration failed.
	 *
	 * @return migration failure, or empty for non-failure outcomes
	 *
	 * @since 0.3.0
	 */
	Optional<Exception> getFailure();
}
