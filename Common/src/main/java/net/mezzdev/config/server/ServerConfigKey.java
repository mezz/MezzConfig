package net.mezzdev.config.server;

import net.mezzdev.config.util.ErrorUtil;

public record ServerConfigKey(String modId, String configFileName) {
	public ServerConfigKey {
		modId = ErrorUtil.checkNotBlank(modId, "modId");
		configFileName = ErrorUtil.checkNotBlank(configFileName, "configFileName");
	}
}
