package net.mezzdev.config.server;

import net.mezzdev.config.schema.ConfigSchemaPathResolver;
import net.mezzdev.config.util.ErrorUtil;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public record ServerConfigPathResolver(
	ServerConfigKey key,
	Path relativeConfigFile,
	Path defaultPath
) implements ConfigSchemaPathResolver {
	public ServerConfigPathResolver {
		key = ErrorUtil.checkNotNull(key, "key");
		relativeConfigFile = ErrorUtil.checkNotNull(relativeConfigFile, "relativeConfigFile");
		defaultPath = ErrorUtil.checkNotNull(defaultPath, "defaultPath");
	}

	@Override
	public Optional<Path> resolvePath() {
		return ServerConfigRuntime.getWorldConfigRoot()
			.map(root -> root.resolve(key.modId()).resolve(relativeConfigFile).normalize());
	}

	@Override
	public Optional<Path> resolveDefaultPath() {
		return ServerConfigRuntime.getWorldConfigRoot().map(ignored -> defaultPath);
	}

	@Override
	public Collection<Path> getPersistentReservationPaths() {
		return List.of(defaultPath);
	}
}
