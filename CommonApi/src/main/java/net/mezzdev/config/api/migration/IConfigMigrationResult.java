package net.mezzdev.config.api.migration;

import org.jetbrains.annotations.ApiStatus;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Structured outcome of a schema's registered legacy migration.
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
	 * Get the normalized absolute destination considered by the migration.
	 * This is empty when the schema had no active local destination.
	 *
	 * @return migration destination, if one was available
	 *
	 * @since 0.3.0
	 */
	Optional<Path> getDestinationPath();

	/**
	 * Get the first existing legacy path selected from the registered ordered paths.
	 *
	 * @return selected normalized absolute legacy path, if one was found
	 *
	 * @since 0.3.0
	 */
	Optional<Path> getLegacyPath();

	/**
	 * Get the backup created from the selected legacy file before the migrator ran.
	 * A failed migration may still have a backup when failure happened after backup creation.
	 *
	 * @return normalized absolute backup path, if a backup was created
	 *
	 * @since 0.3.0
	 */
	Optional<Path> getBackupPath();

	/**
	 * Get the failure reported for {@link ConfigMigrationStatus#FAILED}.
	 *
	 * @return migration failure, or empty for non-failure outcomes
	 *
	 * @since 0.3.0
	 */
	Optional<Exception> getFailure();
}
