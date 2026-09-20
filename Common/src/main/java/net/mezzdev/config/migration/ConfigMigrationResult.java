package net.mezzdev.config.migration;

import net.mezzdev.config.api.migration.ConfigMigrationStatus;
import net.mezzdev.config.api.migration.IConfigMigrationResult;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record ConfigMigrationResult(
	ConfigMigrationStatus status,
	@Nullable Path destinationPath,
	@Nullable Path legacyPath,
	@Nullable Path backupPath,
	int importedValueCount,
	int rejectedValueCount,
	List<String> diagnostics,
	@Nullable Exception failure
) implements IConfigMigrationResult {
	public ConfigMigrationResult {
		destinationPath = normalize(destinationPath);
		legacyPath = normalize(legacyPath);
		backupPath = normalize(backupPath);
		if (importedValueCount < 0) {
			throw new IllegalArgumentException("importedValueCount must not be negative.");
		}
		if (rejectedValueCount < 0) {
			throw new IllegalArgumentException("rejectedValueCount must not be negative.");
		}
		diagnostics = List.copyOf(diagnostics);
	}

	public ConfigMigrationResult(
		ConfigMigrationStatus status,
		@Nullable Path destinationPath,
		@Nullable Path legacyPath,
		@Nullable Path backupPath,
		@Nullable Exception failure
	) {
		this(status, destinationPath, legacyPath, backupPath, 0, 0, List.of(), failure);
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
	public int getImportedValueCount() {
		return importedValueCount;
	}

	@Override
	public int getRejectedValueCount() {
		return rejectedValueCount;
	}

	@Override
	public List<String> getDiagnostics() {
		return diagnostics;
	}

	@Override
	public Optional<Exception> getFailure() {
		return Optional.ofNullable(failure);
	}
}
