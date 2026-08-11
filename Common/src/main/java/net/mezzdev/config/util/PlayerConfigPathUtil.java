package net.mezzdev.config.util;

import java.nio.file.Path;
import java.util.UUID;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class PlayerConfigPathUtil {
	private static final String PLAYERS_DIRECTORY_NAME = "players";

	private PlayerConfigPathUtil() {

	}

	public static Path getPlayerConfigDir(Path configDir, UUID playerId) {
		configDir = ErrorUtil.checkNotNull(configDir, "configDir");
		playerId = ErrorUtil.checkNotNull(playerId, "playerId");
		return configDir.resolve(PLAYERS_DIRECTORY_NAME)
			.resolve(playerId.toString());
	}
}
