package net.mezzdev.config.schema;

import net.mezzdev.config.util.ErrorUtil;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

public record ClientWorldConfigSchemaPathResolver(
	Path relativeConfigFile,
	Supplier<Optional<Path>> clientWorldPathSupplier
) implements ConfigSchemaPathResolver {
	public ClientWorldConfigSchemaPathResolver {
		relativeConfigFile = ErrorUtil.checkNotNull(relativeConfigFile, "relativeConfigFile");
		clientWorldPathSupplier = ErrorUtil.checkNotNull(clientWorldPathSupplier, "clientWorldPathSupplier");
	}

	@Override
	public Optional<Path> resolvePath() {
		return clientWorldPathSupplier.get()
			.map(path -> path.resolve(relativeConfigFile).normalize());
	}
}
