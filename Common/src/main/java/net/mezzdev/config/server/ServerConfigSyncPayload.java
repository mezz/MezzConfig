package net.mezzdev.config.server;

import net.mezzdev.config.util.ErrorUtil;

import java.util.List;

public record ServerConfigSyncPayload(
	ServerConfigKey key,
	List<ServerConfigValueData> values
) {
	public ServerConfigSyncPayload {
		key = ErrorUtil.checkNotNull(key, "key");
		values = List.copyOf(values);
	}
}
