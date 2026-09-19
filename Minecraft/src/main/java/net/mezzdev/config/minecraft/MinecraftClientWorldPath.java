package net.mezzdev.config.minecraft;

import net.mezzdev.config.client.ClientWorldConfigPathUtil;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/** Kept separate so dedicated servers never need to load Minecraft client classes. */
final class MinecraftClientWorldPath {
	private MinecraftClientWorldPath() {}

	static Optional<Path> getWorldPath(Path configDirectory) {
		Minecraft minecraft = Minecraft.getInstance();
		return Optional.ofNullable(minecraft.getConnection())
			.flatMap(listener -> {
				Connection connection = listener.getConnection();
				if (connection.isMemoryConnection()) {
					return Optional.ofNullable(minecraft.getSingleplayerServer())
						.flatMap(MinecraftClientWorldPath::getLevelId)
						.map(ClientWorldConfigPathUtil::getLocalWorldPath);
				}
				Optional<UUID> serverId = ServerConfigRuntime.getRemoteServerId();
				if (serverId.isPresent()) {
					return serverId.map(ClientWorldConfigPathUtil::getServerPath);
				}
				return Optional.ofNullable(minecraft.getCurrentServer())
					.map(server -> ClientWorldConfigPathUtil.getServerPath(server.name, server.ip, server.isLan()));
			})
			.map(configDirectory::resolve);
	}

	private static Optional<String> getLevelId(MinecraftServer server) {
		Path levelId = server.getWorldPath(LevelResource.ROOT).normalize().getFileName();
		return Optional.ofNullable(levelId).map(Path::toString);
	}
}
