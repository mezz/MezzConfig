package net.mezzdev.config.server;

import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.registration.ConfigProvider;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.util.ErrorUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ServerConfigRuntime {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Map<ConfigSchema, Long> SERVER_SCHEMA_VERSIONS = new IdentityHashMap<>();
	private static final ServerConfigPayloadReassembler SYNC_REASSEMBLER = new ServerConfigPayloadReassembler();
	private static volatile @Nullable Path worldConfigRoot;
	private static volatile @Nullable MinecraftServer activeServer;

	private ServerConfigRuntime() {

	}

	public static Optional<Path> getWorldConfigRoot() {
		return Optional.ofNullable(worldConfigRoot);
	}

	public static void validateSnapshot(ServerConfigKey key, List<ServerConfigValueData> values) {
		try {
			ServerConfigSyncPayload payload = new ServerConfigSyncPayload(key, values);
			byte[] encoded = ServerConfigPayloadCodec.encodeSync(payload);
			ServerConfigPayloadChunker.split(encoded, 1);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(
				"Server config schema '%s' cannot be synchronized: %s".formatted(key, getExceptionMessage(e)),
				e
			);
		}
	}

	public static void onServerStarted(MinecraftServer server) {
		activeServer = ErrorUtil.checkNotNull(server, "server");
		worldConfigRoot = server.getWorldPath(LevelResource.ROOT)
			.resolve("serverconfig")
			.normalize();
		SERVER_SCHEMA_VERSIONS.clear();
		ConfigManager manager = getConfigManager();
		manager.onWorldStarted();
		for (ConfigSchema schema : manager.getServerSchemas()) {
			schema.loadIfNeeded();
			SERVER_SCHEMA_VERSIONS.put(schema, schema.getChangeVersion());
		}
	}

	public static void onClientWorldStarted() {
		getConfigManager().onWorldStarted();
	}

	public static void onServerStopped() {
		activeServer = null;
		worldConfigRoot = null;
		SERVER_SCHEMA_VERSIONS.clear();
		getConfigManager().getServerSchemas().forEach(schema -> {
			schema.clearRemoteSnapshot();
			schema.loadIfNeeded();
		});
	}

	public static void onServerSchemaRegistered(ConfigSchema schema) {
		synchronizeServerSchema(schema);
	}

	public static void onServerSchemaChanged(ConfigSchema schema) {
		synchronizeServerSchema(schema);
	}

	private static void synchronizeServerSchema(ConfigSchema schema) {
		MinecraftServer server = activeServer;
		if (server == null) {
			return;
		}
		server.execute(() -> {
			if (activeServer != server) {
				return;
			}
			schema.loadIfNeeded();
			long version = schema.getChangeVersion();
			Long previousVersion = SERVER_SCHEMA_VERSIONS.put(schema, version);
			if (previousVersion == null || previousVersion != version) {
				broadcastSchema(server, schema);
			}
		});
	}

	public static void onPlayerJoin(ServerPlayer player) {
		getConfigManager().getServerSchemas().forEach(schema -> sendSchema(player, schema));
	}

	private static void broadcastSchema(MinecraftServer server, ConfigSchema schema) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sendSchema(player, schema);
		}
	}

	private static void sendSchema(ServerPlayer player, ConfigSchema schema) {
		try {
			ServerConfigSyncPayload payload = new ServerConfigSyncPayload(
				schema.getServerKey(),
				schema.serializeValues()
			);
			ServerConfigNetworking.sendToPlayer(player, payload);
		} catch (RuntimeException e) {
			LOGGER.error("Failed to create synchronized server config payload for {}.", schema.getServerKey(), e);
		}
	}

	public static void handleSync(ServerConfigSyncPayload payload) {
		try {
			getConfigManager().getServerSchema(payload.key())
				.ifPresent(schema -> schema.applyRemoteSnapshot(payload.values()));
		} catch (RuntimeException e) {
			LOGGER.error("Failed to apply synchronized server config schema: {}", payload.key(), e);
		}
	}

	public static void handleSyncChunk(ServerConfigSyncChunkPayload chunk) {
		try {
			SYNC_REASSEMBLER.accept(chunk.payloadInternal())
				.map(ServerConfigPayloadCodec::decodeSync)
				.ifPresent(ServerConfigRuntime::handleSync);
		} catch (RuntimeException e) {
			LOGGER.warn("Rejected malformed synchronized server config fragment: {}", getExceptionMessage(e));
			LOGGER.debug("Malformed synchronized server config fragment details.", e);
		}
	}

	public static void onClientTick() {
		SYNC_REASSEMBLER.expire(System.nanoTime());
	}

	public static void onClientDisconnect() {
		SYNC_REASSEMBLER.clear();
		getConfigManager().getServerSchemas().forEach(ConfigSchema::clearRemoteSnapshot);
	}

	private static String getExceptionMessage(RuntimeException exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return exception.getClass().getSimpleName();
		}
		return message;
	}

	private static ConfigManager getConfigManager() {
		return ConfigProvider.getConfigManager();
	}
}
