package net.mezzdev.config.server;

import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.registration.ConfigProvider;
import net.mezzdev.config.schema.ConfigSchema.ServerSynchronization;
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
	private static final Map<ServerSynchronization, Long> SERVER_SCHEMA_VERSIONS = new IdentityHashMap<>();
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

	public static boolean isClientWorldActive() {
		return CLIENT_CONNECTION.isWorldActive();
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
		for (ServerSynchronization synchronization : manager.getServerSynchronizations()) {
			synchronization.getSchema().loadIfNeeded();
			SERVER_SCHEMA_VERSIONS.put(synchronization, synchronization.getSchema().getChangeVersion());
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
		getConfigManager().onServerStopped();
	}

	public static void onServerSchemaRegistered(ServerSynchronization synchronization) {
		synchronization.addChangeListener(() -> synchronizeServerSchema(synchronization));
		synchronizeServerSchema(synchronization);
	}

	private static void synchronizeServerSchema(ServerSynchronization synchronization) {
		Server server = activeServer;
		if (server == null) {
			return;
		}
		server.execute(() -> {
			if (activeServer != server) {
				return;
			}
			synchronization.getSchema().loadIfNeeded();
			long version = synchronization.getSchema().getChangeVersion();
			Long previousVersion = SERVER_SCHEMA_VERSIONS.put(synchronization, version);
			if (previousVersion == null || previousVersion != version) {
				broadcastSchema(server, synchronization);
			}
		});
	}

	public static void onPlayerJoin(Player player) {
		UUID serverId = activeServerId;
		if (serverId != null) {
			ServerConfigNetworking.sendToPlayer(player, new ServerIdentityPayload(serverId));
		}
		getConfigManager().getServerSynchronizations().forEach(synchronization -> sendSchema(player, synchronization));
	}

	public static void handleServerIdentity(ServerIdentityPayload payload) {
		CLIENT_CONNECTION.handleServerIdentity(payload);
	}

	private static void broadcastSchema(Server server, ServerSynchronization synchronization) {
		for (Player player : server.getPlayers()) {
			sendSchema(player, synchronization);
		}
	}

	private static void sendSchema(Player player, ServerSynchronization synchronization) {
		try {
			ServerConfigSyncPayload payload = new ServerConfigSyncPayload(
				synchronization.getKey(),
				synchronization.serializeValues()
			);
			ServerConfigNetworking.sendToPlayer(player, payload);
		} catch (RuntimeException e) {
			LOGGER.error("Failed to create synchronized server config payload for {}.", synchronization.getKey(), e);
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
