package net.mezzdev.config.registration;

import java.nio.file.Path;

public final class TestConfigPhysicalSideProvider implements ConfigPhysicalSideProvider {
	@Override
	public Path getConfigRoot() {
		return Path.of("build", "test-config");
	}

	@Override
	public boolean isPhysicalClient() {
		return true;
	}
}
