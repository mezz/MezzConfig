package net.mezzdev.config.schema;

import java.nio.file.Path;
import java.util.Optional;

public interface ConfigSchemaPathResolver {
	Optional<Path> resolvePath();

	default Optional<Path> resolveDefaultPath() {
		return Optional.empty();
	}
}
