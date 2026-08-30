package net.mezzdev.config.migration;

import net.mezzdev.config.util.ErrorUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LegacyMigrationPaths {
	private LegacyMigrationPaths() {}

	public static List<Path> normalize(List<Path> legacyPaths) {
		ErrorUtil.checkNotNull(legacyPaths, "legacyPaths");
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
		return List.copyOf(normalizedPaths);
	}
}
