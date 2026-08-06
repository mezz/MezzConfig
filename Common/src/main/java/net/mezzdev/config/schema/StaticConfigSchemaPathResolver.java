package net.mezzdev.config.schema;

import net.mezzdev.config.util.ErrorUtil;

import java.nio.file.Path;
import java.util.Optional;

public record StaticConfigSchemaPathResolver(Path path) implements ConfigSchemaPathResolver {
	public StaticConfigSchemaPathResolver {
		path = ErrorUtil.checkNotNull(path, "path");
	}

	@Override
	public Optional<Path> resolvePath() {
		return Optional.of(path);
	}
}
