package net.mezzdev.config.api.migration;

import java.nio.file.Path;

/**
 * Reads one caller-owned legacy file and queues typed updates for MezzConfig.
 * <p>
 * The migrator owns parsing its legacy format. It must not create or serialize MezzConfig destination files. MezzConfig
 * backs up the selected source before invoking the migrator and preserves the source whether migration succeeds or fails.
 *
 * @since 0.3.0
 */
@FunctionalInterface
public interface IConfigMigrator {
	/**
	 * Read a legacy file and queue all migrated updates in the supplied transaction context.
	 * Throwing aborts the complete transaction.
	 *
	 * @param legacyPath normalized absolute path to the selected legacy file
	 * @param context context for typed config value and sorting updates
	 * @throws Exception if the legacy file cannot be read or migrated
	 *
	 * @since 0.3.0
	 */
	void migrate(Path legacyPath, IConfigMigrationContext context) throws Exception;

	/**
	 * Handle the final outcome of this registered migration.
	 * <p>
	 * MezzConfig calls this exactly once after it finishes migration processing, including when migration is skipped or
	 * fails. An exception thrown by this method is logged and does not change the completed migration outcome.
	 *
	 * @param result final structured migration result
	 *
	 * @since 0.3.0
	 */
	default void onMigrationComplete(IConfigMigrationResult result) {}
}
