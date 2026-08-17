package net.mezzdev.config.registration;

public final class TestConfigPhysicalSideProvider implements ConfigPhysicalSideProvider {
	@Override
	public boolean isPhysicalClient() {
		return true;
	}
}
