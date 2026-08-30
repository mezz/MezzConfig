package net.mezzdev.config.client;

import net.minecraft.FileUtil;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.mezzdev.config.util.ErrorUtil;

import java.net.IDN;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public final class ClientWorldConfigPathUtil {
	private static final Path WORLD_DIR_PATH = Path.of("world");
	private static final Path DEFAULT_DIR_PATH = WORLD_DIR_PATH.resolve("default");
	private static final Path LOCAL_DIR_PATH = WORLD_DIR_PATH.resolve("local");
	private static final Path SERVER_DIR_PATH = WORLD_DIR_PATH.resolve("server");

	private ClientWorldConfigPathUtil() {

	}

	public static Optional<Path> getWorldPath(Path configDirectory) {
		Path configDir = ErrorUtil.checkNotNull(configDirectory, "configDirectory");
		Minecraft minecraft = Minecraft.getInstance();
		return Optional.ofNullable(minecraft.getConnection())
			.flatMap(clientPacketListener -> {
				Connection connection = clientPacketListener.getConnection();
				if (connection.isMemoryConnection()) {
					return Optional.ofNullable(minecraft.getSingleplayerServer())
						.flatMap(ClientWorldConfigPathUtil::getLevelId)
						.map(ClientWorldConfigPathUtil::sanitizePathName)
						.map(levelId -> LOCAL_DIR_PATH.resolve(levelId));
				}
				return Optional.ofNullable(minecraft.getCurrentServer())
					.map(serverData -> getServerPath(serverData.name, serverData.ip, serverData.isLan()));
			})
			.map(configDir::resolve);
	}

	private static Optional<String> getLevelId(MinecraftServer minecraftServer) {
		Path worldPath = minecraftServer.getWorldPath(LevelResource.ROOT)
			.normalize();
		Path levelId = worldPath.getFileName();
		return Optional.ofNullable(levelId)
			.map(Path::toString);
	}

	public static Path getServerPath(String serverName, String serverAddress) {
		return getServerPath(serverName, serverAddress, false);
	}

	public static Path getServerPath(String serverName, String serverAddress, boolean isLan) {
		String name = ErrorUtil.checkNotNull(serverName, "serverName");
		String address = ErrorUtil.checkNotNull(serverAddress, "serverAddress");
		if (isLan) {
			return getNamedServerPath("%s (LAN connection)".formatted(name));
		}

		return parseServerAddress(address)
			.map(serverAddressAndPort -> {
				String addressName = getAddressName(serverAddressAndPort);
				return getNamedServerPath("%s (%s)".formatted(name, addressName));
			})
			.orElseGet(() -> getNamedServerPath("%s (%s)".formatted(name, address)));
	}

	public static Path getServerDirPath() {
		return SERVER_DIR_PATH;
	}

	public static Path getDefaultWorldPath(Path configDirectory) {
		Path configDir = ErrorUtil.checkNotNull(configDirectory, "configDirectory");
		return configDir.resolve(DEFAULT_DIR_PATH);
	}

	private static String getAddressName(ServerAddressAndPort serverAddressAndPort) {
		String host = serverAddressAndPort.host();
		host = sanitizePathName(host.toLowerCase(Locale.ROOT));
		int port = serverAddressAndPort.port();
		if (port != SharedConstants.DEFAULT_MINECRAFT_PORT) {
			return "%s %d".formatted(host, port);
		}
		return host;
	}

	private static Optional<ServerAddressAndPort> parseServerAddress(String serverAddress) {
		try {
			ServerAddressAndPort addressAndPort = parseServerAddressUnchecked(serverAddress);
			if (!addressAndPort.host().isBlank()) {
				return Optional.of(addressAndPort);
			}
		} catch (IllegalArgumentException ignored) {

		}
		return Optional.empty();
	}

	private static ServerAddressAndPort parseServerAddressUnchecked(String serverAddress) {
		serverAddress = serverAddress.trim();
		if (serverAddress.startsWith("[")) {
			int hostEnd = serverAddress.indexOf(']');
			if (hostEnd < 0) {
				throw new IllegalArgumentException("Invalid bracketed server address: " + serverAddress);
			}
			String host = serverAddress.substring(1, hostEnd);
			int port = SharedConstants.DEFAULT_MINECRAFT_PORT;
			if (serverAddress.length() > hostEnd + 1) {
				if (serverAddress.charAt(hostEnd + 1) != ':') {
					throw new IllegalArgumentException("Invalid bracketed server address: " + serverAddress);
				}
				port = parsePort(serverAddress.substring(hostEnd + 2));
			}
			return new ServerAddressAndPort(host, port);
		}

		int firstColon = serverAddress.indexOf(':');
		int lastColon = serverAddress.lastIndexOf(':');
		if (firstColon >= 0 && firstColon == lastColon) {
			String host = serverAddress.substring(0, firstColon);
			int port = parsePort(serverAddress.substring(firstColon + 1));
			return new ServerAddressAndPort(toAscii(host), port);
		}

		return new ServerAddressAndPort(toAscii(serverAddress), SharedConstants.DEFAULT_MINECRAFT_PORT);
	}

	private static String toAscii(String host) {
		return IDN.toASCII(host);
	}

	private static int parsePort(String value) {
		int port = Integer.parseInt(value);
		if (port < 0 || port > 65_535) {
			throw new IllegalArgumentException("Invalid port: " + value);
		}
		return port;
	}

	private static Path getNamedServerPath(String name) {
		name = sanitizePathName(name);
		return SERVER_DIR_PATH.resolve(name);
	}

	private static String sanitizePathName(String filename) {
		String sanitized = FileUtil.sanitizeName(filename)
			.trim();
		if (sanitized.isEmpty()) {
			return "_";
		}
		if (!FileUtil.isPathPortable(Path.of(sanitized))) {
			return "_%s_".formatted(sanitized);
		}
		return sanitized;
	}
	private record ServerAddressAndPort(String host, int port) {}
}
