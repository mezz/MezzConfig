package net.mezzdev.config.file;

import net.mezzdev.config.util.ErrorUtil;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class ConfigPathReservations {
	private final Map<Path, Reservation> reservationsByPath = new HashMap<>();
	private final Map<Object, Set<Path>> pathsByOwner = new IdentityHashMap<>();

	public synchronized void replace(
		Object owner,
		String description,
		Collection<Path> paths
	) {
		owner = ErrorUtil.checkNotNull(owner, "owner");
		description = ErrorUtil.checkNotNull(description, "description");
		paths = ErrorUtil.checkNotNull(paths, "paths");
		Set<Path> normalizedPaths = new LinkedHashSet<>();
		for (Path path : paths) {
			path = ErrorUtil.checkNotNull(path, "path")
				.toAbsolutePath()
				.normalize();
			Reservation existing = reservationsByPath.get(path);
			if (existing != null && existing.owner() != owner) {
				throw new IllegalArgumentException(
					"Cannot reserve config file path for %s because it is already reserved by %s: %s".formatted(
						description,
						existing.description(),
						path
					)
				);
			}
			normalizedPaths.add(path);
		}

		Set<Path> previousPaths = pathsByOwner.getOrDefault(owner, Set.of());
		for (Path previousPath : previousPaths) {
			if (!normalizedPaths.contains(previousPath)) {
				reservationsByPath.remove(previousPath);
			}
		}
		for (Path path : normalizedPaths) {
			reservationsByPath.put(path, new Reservation(owner, description));
		}
		if (normalizedPaths.isEmpty()) {
			pathsByOwner.remove(owner);
		} else {
			pathsByOwner.put(owner, Set.copyOf(normalizedPaths));
		}
	}

	private record Reservation(Object owner, String description) {}
}
