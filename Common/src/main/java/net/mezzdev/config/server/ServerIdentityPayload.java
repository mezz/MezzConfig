package net.mezzdev.config.server;

import net.mezzdev.config.util.ErrorUtil;
import java.util.UUID;

public record ServerIdentityPayload(UUID serverId) {
	public ServerIdentityPayload {
		serverId = ErrorUtil.checkNotNull(serverId, "serverId");
	}
}
