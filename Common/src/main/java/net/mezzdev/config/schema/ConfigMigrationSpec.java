package net.mezzdev.config.schema;

import net.mezzdev.config.api.migration.IConfigMigrator;
import net.mezzdev.config.util.ErrorUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

record ConfigMigrationSpec(
	List<Path> legacyPaths,
	IConfigMigrator migrator
) {
	ConfigMigrationSpec {
		ErrorUtil.checkNotNull(legacyPaths, "legacyPaths");
		migrator = ErrorUtil.checkNotNull(migrator, "migrator");
		if (legacyPaths.isEmpty()) {
			throw new IllegalArgumentException("legacyPaths must not be empty.");
		}
		List<Path> normalizedPaths = new ArrayList<>(legacyPaths.size());
		Set<Path> uniquePaths = new HashSet<>();
		for (Path path : legacyPaths) {
			Path normalizedPath = ErrorUtil.checkNotNull(path, "legacyPaths entry")
				.toAbsolutePath()
				.normalize();
			if (!uniquePaths.add(normalizedPath)) {
				throw new IllegalArgumentException("legacyPaths must not contain duplicate normalized paths: " + normalizedPath);
			}
			normalizedPaths.add(normalizedPath);
		}
		legacyPaths = List.copyOf(normalizedPaths);
	}
}
