package net.mezzdev.config.schema;

import net.mezzdev.config.api.migration.ConfigMigrationStatus;
import net.mezzdev.config.api.migration.IConfigMigrationResult;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;

record ConfigMigrationResult(
	ConfigMigrationStatus status,
	@Nullable Path destinationPath,
	@Nullable Path legacyPath,
	@Nullable Path backupPath,
	@Nullable Exception failure
) implements IConfigMigrationResult {
	ConfigMigrationResult {
		destinationPath = normalize(destinationPath);
		legacyPath = normalize(legacyPath);
		backupPath = normalize(backupPath);
	}

	private static @Nullable Path normalize(@Nullable Path path) {
		if (path == null) {
			return null;
		}
		return path.toAbsolutePath().normalize();
	}

	@Override
	public ConfigMigrationStatus getStatus() {
		return status;
	}

	@Override
	public Optional<Path> getDestinationPath() {
		return Optional.ofNullable(destinationPath);
	}

	@Override
	public Optional<Path> getLegacyPath() {
		return Optional.ofNullable(legacyPath);
	}

	@Override
	public Optional<Path> getBackupPath() {
		return Optional.ofNullable(backupPath);
	}

	@Override
	public Optional<Exception> getFailure() {
		return Optional.ofNullable(failure);
	}
}
