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

import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class ServerConfigRuntime {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Map<ConfigSchema, Long> SERVER_SCHEMA_VERSIONS = new IdentityHashMap<>();
	private static final ServerConfigPayloadReassembler SYNC_REASSEMBLER = new ServerConfigPayloadReassembler();
	private static final AtomicReference<UUID> REMOTE_SERVER_ID = new AtomicReference<>();
	private static volatile @Nullable Path worldConfigRoot;
	private static volatile @Nullable MinecraftServer activeServer;
	private static volatile @Nullable UUID activeServerId;

	private ServerConfigRuntime() {

	}

	public static Optional<Path> getWorldConfigRoot() {
		return Optional.ofNullable(worldConfigRoot);
	}

	public static Optional<UUID> getRemoteServerId() {
		return Optional.ofNullable(REMOTE_SERVER_ID.get());
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
		Path worldRoot = server.getWorldPath(LevelResource.ROOT).normalize();
		worldConfigRoot = worldRoot.resolve("serverconfig").normalize();
		activeServerId = getOrCreateServerId(worldRoot);
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
		activeServerId = null;
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
		UUID serverId = activeServerId;
		if (serverId != null) {
			ServerConfigNetworking.sendToPlayer(player, new ServerIdentityPayload(serverId));
		}
		getConfigManager().getServerSchemas().forEach(schema -> sendSchema(player, schema));
	}

	public static void handleServerIdentity(ServerIdentityPayload payload) {
		UUID serverId = payload.serverId();
		if (REMOTE_SERVER_ID.compareAndSet(null, serverId)) {
			getConfigManager().onClientServerIdentityReceived();
			return;
		}
		UUID currentServerId = REMOTE_SERVER_ID.get();
		if (!serverId.equals(currentServerId)) {
			LOGGER.warn("Ignored a conflicting server identity for the current connection.");
		}
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
		REMOTE_SERVER_ID.set(null);
		getConfigManager().getServerSchemas().forEach(ConfigSchema::clearRemoteSnapshot);
	}

	private static @Nullable UUID getOrCreateServerId(Path worldRoot) {
		try {
			return ServerIdentityStore.getOrCreate(worldRoot);
		} catch (IOException | RuntimeException e) {
			LOGGER.warn(
				"Could not load or save a stable server identity. Clients will use their local server identity fallback.",
				e
			);
			return null;
		}
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
