package net.mezzdev.config.server;

import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.util.ErrorUtil;

public record ServerConfigKey(String modId, String configFileName) {
	public ServerConfigKey {
		modId = ConfigSchema.validateModId(modId);
		configFileName = ErrorUtil.checkNotNull(configFileName, "configFileName");
		if (configFileName.isBlank()) {
			throw new IllegalArgumentException("configFileName must not be blank.");
		}
	}
}
