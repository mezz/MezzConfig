package net.mezzdev.config.sorting;

import net.mezzdev.config.api.migration.ISortingConfigMigrator;
import net.mezzdev.config.migration.LegacyMigrationPaths;
import net.mezzdev.config.util.ErrorUtil;

import java.nio.file.Path;
import java.util.List;

record SortingConfigMigrationSpec<T>(
	List<Path> legacyPaths,
	ISortingConfigMigrator<T> migrator
) {
	SortingConfigMigrationSpec {
		legacyPaths = LegacyMigrationPaths.normalize(legacyPaths);
		migrator = ErrorUtil.checkNotNull(migrator, "migrator");
	}
}
