package net.mezzdev.config.registration;

import java.nio.file.Path;
import java.util.Optional;

public final class TestConfigPhysicalSideProvider implements ConfigPhysicalSideProvider {
	@Override
	public Path getConfigRoot() {
		return Path.of("build", "test-config");
	}

	@Override
	public boolean isPhysicalClient() {
		return true;
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return true;
	}

	@Override
	public Optional<Path> getClientWorldPath(Path configDirectory) {
		return Optional.empty();
	}

	@Override
	public boolean hasTranslation(String key) {
		return false;
	}

	@Override
	public String translate(String key, Object... arguments) {
		return key;
	}
}
