package net.mezzdev.config.forge;

public final class ConfigForgeClientSafeRunner {
	private final ConfigForgeNetwork network;

	public ConfigForgeClientSafeRunner(ConfigForgeNetwork network) {
		this.network = network;
	}

	public void registerClient() {
		ConfigForgeClient.register(network);
	}
}
