package net.mezzdev.config.schema;

import java.nio.file.Path;
import java.util.Optional;

public interface ConfigSchemaPathResolver {
	Optional<Path> resolvePath();
}
