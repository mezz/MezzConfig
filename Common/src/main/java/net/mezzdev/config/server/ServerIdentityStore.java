package net.mezzdev.config.server;

import net.mezzdev.config.file.ConfigFileUtil;
import net.mezzdev.config.util.ErrorUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

final class ServerIdentityStore {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final int MAX_SERVER_ID_BYTES = 64;
	private static final Path SERVER_ID_PATH = Path.of("data", "mezz_config", "server-id.txt");

	private ServerIdentityStore() {

	}

	public static UUID getOrCreate(Path worldRoot) throws IOException {
		Path path = getPath(worldRoot);
		if (Files.exists(path)) {
			try {
				return read(path);
			} catch (IllegalArgumentException e) {
				Path backupPath = ConfigFileUtil.backUpFile(path);
				LOGGER.warn("Replaced invalid server identity file '{}'; the original was backed up to '{}'.", path, backupPath);
			}
		}

		UUID serverId = UUID.randomUUID();
		ConfigFileUtil.writeUsingTempFile(path, List.of(serverId.toString()));
		return serverId;
	}

	static Path getPath(Path worldRoot) {
		return ErrorUtil.checkNotNull(worldRoot, "worldRoot")
			.resolve(SERVER_ID_PATH)
			.normalize();
	}

	private static UUID read(Path path) throws IOException {
		if (Files.size(path) > MAX_SERVER_ID_BYTES) {
			throw new IllegalArgumentException("Server identity file is too large.");
		}
		String serialized = Files.readString(path, StandardCharsets.UTF_8).trim();
		UUID serverId;
		try {
			serverId = UUID.fromString(serialized);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Server identity file does not contain a UUID.", e);
		}
		if (!serverId.toString().equalsIgnoreCase(serialized)) {
			throw new IllegalArgumentException("Server identity file does not contain a canonical UUID.");
		}
		return serverId;
	}
}
