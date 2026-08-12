package net.mezzdev.config.server;

import net.mezzdev.config.util.ErrorUtil;

import java.util.List;

public record ServerConfigUpdatePayload(
	ServerConfigKey key,
	long requestId,
	List<ServerConfigValueData> values
) {
	public ServerConfigUpdatePayload {
		key = ErrorUtil.checkNotNull(key, "key");
		values = List.copyOf(values);
		if (requestId <= 0) {
			throw new IllegalArgumentException("requestId must be positive.");
		}
	}
}
