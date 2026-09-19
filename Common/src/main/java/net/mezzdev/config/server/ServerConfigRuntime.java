package net.mezzdev.config.server;

import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.registration.ConfigProvider;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.util.ErrorUtil;
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

public final class ServerConfigRuntime {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Map<ConfigSchema, Long> SERVER_SCHEMA_VERSIONS = new IdentityHashMap<>();
	private static final ServerConfigClientConnection CLIENT_CONNECTION = new ServerConfigClientConnection(
		ServerConfigRuntime::getConfigManager
	);
	private static volatile @Nullable Path worldConfigRoot;
	private static volatile @Nullable Server activeServer;
	private static volatile @Nullable UUID activeServerId;

	public interface Server {
		Path getWorldRoot();
		void execute(Runnable task);
		Iterable<? extends Player> getPlayers();
	}

	public interface Player {
		String getName();
		boolean sendIdentity(UUID serverId);
		boolean sendSync(byte[] data);
	}

	private ServerConfigRuntime() {

	}

	public static Optional<Path> getWorldConfigRoot() {
		return Optional.ofNullable(worldConfigRoot);
	}

	public static Optional<UUID> getRemoteServerId() {
		return CLIENT_CONNECTION.getRemoteServerId();
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

	public static void onServerStarted(Server server) {
		activeServer = ErrorUtil.checkNotNull(server, "server");
		Path worldRoot = server.getWorldRoot().normalize();
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
		CLIENT_CONNECTION.onWorldStarted();
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
		Server server = activeServer;
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

	public static void onPlayerJoin(Player player) {
		UUID serverId = activeServerId;
		if (serverId != null) {
			ServerConfigNetworking.sendToPlayer(player, new ServerIdentityPayload(serverId));
		}
		getConfigManager().getServerSchemas().forEach(schema -> sendSchema(player, schema));
	}

	public static void handleServerIdentity(ServerIdentityPayload payload) {
		CLIENT_CONNECTION.handleServerIdentity(payload);
	}

	private static void broadcastSchema(Server server, ConfigSchema schema) {
		for (Player player : server.getPlayers()) {
			sendSchema(player, schema);
		}
	}

	private static void sendSchema(Player player, ConfigSchema schema) {
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
		CLIENT_CONNECTION.handleSync(payload);
	}

	public static void handleSyncChunk(ServerConfigSyncChunkPayload chunk) {
		CLIENT_CONNECTION.handleSyncChunk(chunk);
	}

	public static void onClientTick() {
		CLIENT_CONNECTION.onTick();
	}

	public static void onClientDisconnect() {
		CLIENT_CONNECTION.onDisconnect();
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
