package net.mezzdev.config.schema;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConfigSchemaPathResolver {
	Optional<Path> resolvePath();

	default Optional<Path> resolveDefaultPath() {
		return Optional.empty();
	}

	/** Return paths owned by this schema even while its active context is unavailable. */
	default Collection<Path> getPersistentReservationPaths() {
		return List.of();
	}
}
