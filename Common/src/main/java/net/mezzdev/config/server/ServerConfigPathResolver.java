package net.mezzdev.config.server;

import net.mezzdev.config.schema.ConfigSchemaPathResolver;
import net.mezzdev.config.util.ErrorUtil;

import java.nio.file.Path;
import java.util.Optional;

public record ServerConfigPathResolver(
	ServerConfigKey key,
	Path relativeConfigFile,
	boolean authoritative
) implements ConfigSchemaPathResolver {
	public ServerConfigPathResolver {
		key = ErrorUtil.checkNotNull(key, "key");
		relativeConfigFile = ErrorUtil.checkNotNull(relativeConfigFile, "relativeConfigFile");
	}

	@Override
	public Optional<Path> resolvePath() {
		if (!authoritative) {
			return Optional.empty();
		}
		return ServerConfigRuntime.getWorldConfigRoot()
			.map(root -> root.resolve(key.modId()).resolve(relativeConfigFile).normalize());
	}
}
