package net.mezzdev.config.api.migration;

/**
 * Describes the outcome of a registered legacy config migration.
 *
 * @since 0.3.0
 */
public enum ConfigMigrationStatus {
	/** The destination did not exist and the selected legacy file was migrated successfully. */
	MIGRATED,
	/** The destination already existed, so migration was not attempted. */
	SKIPPED_DESTINATION_EXISTS,
	/** None of the registered legacy files existed, so migration was not attempted. */
	SKIPPED_NO_LEGACY_FILE,
	/** The schema had no active local destination when it was registered, so migration was not attempted. */
	SKIPPED_INACTIVE,
	/** Migration failed without applying any of its updates. */
	FAILED
}
