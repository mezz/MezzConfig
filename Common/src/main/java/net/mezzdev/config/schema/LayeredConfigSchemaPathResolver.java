package net.mezzdev.config.schema;

import net.mezzdev.config.util.ErrorUtil;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public record LayeredConfigSchemaPathResolver(
	Path defaultPath,
	ConfigSchemaPathResolver playerPathResolver
) implements ConfigSchemaPathResolver {
	public LayeredConfigSchemaPathResolver {
		defaultPath = ErrorUtil.checkNotNull(defaultPath, "defaultPath");
		playerPathResolver = ErrorUtil.checkNotNull(playerPathResolver, "playerPathResolver");
	}

	@Override
	public Optional<Path> resolvePath() {
		return playerPathResolver.resolvePath();
	}

	@Override
	public Optional<Path> resolveDefaultPath() {
		return Optional.of(defaultPath);
	}

	@Override
	public Collection<Path> getPersistentReservationPaths() {
		return List.of(defaultPath);
	}
}
