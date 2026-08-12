package net.mezzdev.config.server;

import net.mezzdev.config.util.ErrorUtil;

import java.util.List;

public record ServerConfigSyncPayload(
	ServerConfigKey key,
	long requestId,
	boolean accepted,
	boolean canEdit,
	String errorMessage,
	List<ServerConfigValueData> values
) {
	private static final int MAX_ERROR_LENGTH = 1_024;

	public ServerConfigSyncPayload {
		key = ErrorUtil.checkNotNull(key, "key");
		if (errorMessage == null) {
			errorMessage = "";
		}
		if (errorMessage.length() > MAX_ERROR_LENGTH) {
			errorMessage = errorMessage.substring(0, MAX_ERROR_LENGTH);
		}
		values = List.copyOf(values);
		if (requestId < 0) {
			throw new IllegalArgumentException("requestId must not be negative.");
		}
	}
}
