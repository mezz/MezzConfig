package net.mezzdev.config.schema;

import net.mezzdev.config.api.migration.IConfigMigrator;
import net.mezzdev.config.api.migration.IConfigMigrationResult;
import net.mezzdev.config.migration.LegacyMigrationPaths;
import net.mezzdev.config.util.ErrorUtil;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

record ConfigMigrationSpec(
	List<Path> legacyPaths,
	@Nullable IConfigMigrator migrator
) {
	ConfigMigrationSpec {
		legacyPaths = LegacyMigrationPaths.normalize(legacyPaths);
	}

	static ConfigMigrationSpec alternateSources(List<Path> legacyPaths) {
		return new ConfigMigrationSpec(legacyPaths, null);
	}

	static ConfigMigrationSpec custom(List<Path> legacyPaths, IConfigMigrator migrator) {
		return new ConfigMigrationSpec(legacyPaths, ErrorUtil.checkNotNull(migrator, "migrator"));
	}

	boolean loadsAlternateSource() {
		return migrator == null;
	}

	void migrate(Path legacyPath, ConfigMigrationContext context) throws Exception {
		if (migrator == null) {
			throw new IllegalStateException("Alternate-source migrations are handled by MezzConfig.");
		}
		migrator.migrate(legacyPath, context);
	}

	void onMigrationComplete(IConfigMigrationResult result) {
		if (migrator != null) {
			migrator.onMigrationComplete(result);
		}
	}
}
