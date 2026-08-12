package net.mezzdev.config.server;

import net.mezzdev.config.api.files.ConfigManagers;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.config.value.ConfigValueUpdate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ServerConfigRuntime {
	private static final Logger LOGGER = LogManager.getLogger();
	static final Duration CLIENT_REQUEST_TIMEOUT = Duration.ofSeconds(15);
	static final int MAX_PENDING_CLIENT_REQUESTS = 128;
	private static final long CLIENT_REQUEST_TIMEOUT_NANOS = CLIENT_REQUEST_TIMEOUT.toNanos();
	private static final int MAX_ERROR_MESSAGE_CHARACTERS = ServerConfigPayloadCodec.MAX_ERROR_MESSAGE_BYTES / 4;
	private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();
	private static final Map<Long, PendingRequest> PENDING_REQUESTS = new ConcurrentHashMap<>();
	private static final Set<CompletableFuture<Void>> PENDING_LOCAL_REQUESTS = ConcurrentHashMap.newKeySet();
	private static final Object LOCAL_REQUEST_LOCK = new Object();
	private static final Map<ConfigSchema, Long> SERVER_SCHEMA_VERSIONS = new IdentityHashMap<>();
	private static final Map<UUID, Boolean> PLAYER_EDIT_PERMISSIONS = new HashMap<>();
	private static final Map<UUID, ServerConfigPayloadReassembler> UPDATE_REASSEMBLERS = new HashMap<>();
	private static final ServerConfigPayloadReassembler SYNC_REASSEMBLER = new ServerConfigPayloadReassembler();
	private static volatile @Nullable Path worldConfigRoot;
	private static volatile @Nullable MinecraftServer activeServer;
	private static volatile @Nullable ConfigManager serverConfigManager;

	private ServerConfigRuntime() {

	}

	public static Optional<Path> getWorldConfigRoot() {
		return Optional.ofNullable(worldConfigRoot);
	}

	public static void setServerConfigManager(ConfigManager configManager) {
		serverConfigManager = ErrorUtil.checkNotNull(configManager, "configManager");
	}

	public static void linkClientSchemas(ConfigManager clientConfigManager) {
		ErrorUtil.checkNotNull(clientConfigManager, "clientConfigManager");
		getServerConfigManager().ifPresent(serverManager -> {
			for (ConfigSchema clientSchema : clientConfigManager.getServerSchemas()) {
				serverManager.getServerSchema(clientSchema.getServerKey())
					.ifPresent(clientSchema::linkServerCounterpart);
			}
		});
	}

	public static boolean isServerThread() {
		MinecraftServer server = activeServer;
		return server != null && server.isSameThread();
	}

	public static void onServerStarted(MinecraftServer server) {
		synchronized (LOCAL_REQUEST_LOCK) {
			activeServer = ErrorUtil.checkNotNull(server, "server");
		}
		worldConfigRoot = server.getWorldPath(LevelResource.ROOT)
			.resolve("serverconfig")
			.normalize();
		SERVER_SCHEMA_VERSIONS.clear();
		PLAYER_EDIT_PERMISSIONS.clear();
		UPDATE_REASSEMBLERS.clear();
		getServerConfigManager().ifPresent(manager -> {
			for (ConfigSchema schema : manager.getServerSchemas()) {
				schema.loadIfNeeded();
				SERVER_SCHEMA_VERSIONS.put(schema, schema.getChangeVersion());
			}
		});
	}

	public static void onServerStopped() {
		List<CompletableFuture<Void>> pendingLocalRequests;
		synchronized (LOCAL_REQUEST_LOCK) {
			activeServer = null;
			pendingLocalRequests = List.copyOf(PENDING_LOCAL_REQUESTS);
			PENDING_LOCAL_REQUESTS.clear();
		}
		worldConfigRoot = null;
		SERVER_SCHEMA_VERSIONS.clear();
		PLAYER_EDIT_PERMISSIONS.clear();
		UPDATE_REASSEMBLERS.clear();
		IllegalStateException exception = new IllegalStateException("The local server stopped before the config update completed.");
		pendingLocalRequests.forEach(future -> future.completeExceptionally(exception));
		getServerConfigManager().ifPresent(manager -> manager.getServerSchemas().forEach(schema -> {
			schema.clearRemoteSnapshot();
			schema.loadIfNeeded();
		}));
	}

	public static void onServerTick() {
		MinecraftServer server = activeServer;
		if (server == null) {
			return;
		}
		expireUpdateReassemblers(System.nanoTime());
		getServerConfigManager().ifPresent(manager -> {
			for (ConfigSchema schema : manager.getServerSchemas()) {
				schema.loadIfNeeded();
				long version = schema.getChangeVersion();
				Long previousVersion = SERVER_SCHEMA_VERSIONS.put(schema, version);
				if (previousVersion != null && previousVersion != version) {
					broadcastSchema(server, schema, null, 0, true, "");
				}
			}
		});
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean canEdit = hasEditPermission(player);
			Boolean previousCanEdit = PLAYER_EDIT_PERMISSIONS.put(player.getUUID(), canEdit);
			if (previousCanEdit != null && previousCanEdit != canEdit) {
				getServerConfigManager().ifPresent(manager -> manager.getServerSchemas().forEach(schema -> sendSchema(player, schema, 0, true, "")));
			}
		}
	}

	public static void onPlayerJoin(ServerPlayer player) {
		PLAYER_EDIT_PERMISSIONS.put(player.getUUID(), hasEditPermission(player));
		getServerConfigManager().ifPresent(manager -> manager.getServerSchemas().forEach(schema -> sendSchema(player, schema, 0, true, "")
		));
	}

	public static void onPlayerDisconnect(ServerPlayer player) {
		UUID playerId = player.getUUID();
		PLAYER_EDIT_PERMISSIONS.remove(playerId);
		UPDATE_REASSEMBLERS.remove(playerId);
	}

	public static void handleUpdateChunk(ServerPlayer player, ServerConfigUpdateChunkPayload chunk) {
		UUID playerId = player.getUUID();
		ServerConfigPayloadReassembler reassembler = UPDATE_REASSEMBLERS.computeIfAbsent(
			playerId,
			ignored -> new ServerConfigPayloadReassembler()
		);
		try {
			reassembler.accept(chunk.payloadInternal())
				.ifPresent(data -> {
					handleUpdate(player, ServerConfigPayloadCodec.decodeUpdate(data));
				});
		} catch (RuntimeException e) {
			LOGGER.warn(
				"Rejected malformed server config update fragment from {}: {}",
				player.getGameProfile().getName(),
				getExceptionMessage(e)
			);
			LOGGER.debug("Malformed server config update fragment details.", e);
		} finally {
			if (reassembler.isEmpty()) {
				UPDATE_REASSEMBLERS.remove(playerId, reassembler);
			}
		}
	}

	private static void expireUpdateReassemblers(long nowNanos) {
		Iterator<Map.Entry<UUID, ServerConfigPayloadReassembler>> iterator = UPDATE_REASSEMBLERS.entrySet().iterator();
		while (iterator.hasNext()) {
			ServerConfigPayloadReassembler reassembler = iterator.next().getValue();
			reassembler.expire(nowNanos);
			if (reassembler.isEmpty()) {
				iterator.remove();
			}
		}
	}

	public static void handleUpdate(ServerPlayer player, ServerConfigUpdatePayload payload) {
		Optional<ConfigManager> manager = getServerConfigManager();
		Optional<ConfigSchema> schema = manager.flatMap(configManager -> configManager.getServerSchema(payload.key()));
		if (schema.isEmpty()) {
			sendRejected(player, payload, "The server does not have this config schema.", null);
			return;
		}
		ConfigSchema configSchema = schema.orElseThrow();
		if (!hasEditPermission(player)) {
			sendRejected(player, payload, "Server operator permission is required.", configSchema);
			return;
		}
		try {
			List<ConfigValueUpdate<?>> updates = configSchema.deserializeUpdates(payload.values(), false);
			configSchema.applyServerUpdates(updates);
		} catch (RuntimeException e) {
			LOGGER.debug("Rejected server config update from {} for {}.", player.getGameProfile().getName(), payload.key(), e);
			sendRejected(player, payload, e.getMessage(), configSchema);
			return;
		}
		SERVER_SCHEMA_VERSIONS.put(configSchema, configSchema.getChangeVersion());
		MinecraftServer server = player.getServer();
		if (server != null) {
			broadcastSchema(server, configSchema, player, payload.requestId(), true, "");
		} else {
			sendSchema(player, configSchema, payload.requestId(), true, "");
		}
	}

	private static void sendRejected(
		ServerPlayer player,
		ServerConfigUpdatePayload payload,
		@Nullable String errorMessage,
		@Nullable ConfigSchema schema
	) {
		try {
			String message = getErrorMessage(errorMessage);
			List<ServerConfigValueData> values = getSerializedValues(schema);
			ServerConfigSyncPayload response = new ServerConfigSyncPayload(
				payload.key(),
				payload.requestId(),
				false,
				hasEditPermission(player),
				message,
				values
			);
			ServerConfigNetworking.sendToPlayer(player, response);
		} catch (RuntimeException e) {
			LOGGER.error("Failed to create rejected server config response for {}.", payload.key(), e);
		}
	}

	private static String getErrorMessage(@Nullable String errorMessage) {
		if (errorMessage == null || errorMessage.isBlank()) {
			return "The server rejected this config update.";
		}
		return summarizeErrorMessage(errorMessage);
	}

	private static List<ServerConfigValueData> getSerializedValues(@Nullable ConfigSchema schema) {
		if (schema == null) {
			return List.of();
		}
		return schema.serializeValues();
	}

	private static void broadcastSchema(
		MinecraftServer server,
		ConfigSchema schema,
		@Nullable ServerPlayer requester,
		long requestId,
		boolean accepted,
		String errorMessage
	) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == requester) {
				sendSchema(player, schema, requestId, accepted, errorMessage);
			} else {
				sendSchema(player, schema, 0, accepted, "");
			}
		}
	}

	private static void sendSchema(
		ServerPlayer player,
		ConfigSchema schema,
		long requestId,
		boolean accepted,
		String errorMessage
	) {
		try {
			ServerConfigSyncPayload payload = new ServerConfigSyncPayload(
				schema.getServerKey(),
				requestId,
				accepted,
				hasEditPermission(player),
				errorMessage,
				schema.serializeValues()
			);
			ServerConfigNetworking.sendToPlayer(player, payload);
		} catch (RuntimeException e) {
			LOGGER.error("Failed to create synchronized server config payload for {}.", schema.getServerKey(), e);
		}
	}

	private static boolean hasEditPermission(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		return server != null && player.createCommandSourceStack().hasPermission(server.getOperatorUserPermissionLevel());
	}

	public static CompletableFuture<Void> requestUpdate(
		ConfigSchema schema,
		List<? extends ConfigValueUpdate<?>> updates
	) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		long requestId;
		PendingRequest pending;
		synchronized (PENDING_REQUESTS) {
			if (PENDING_REQUESTS.size() >= MAX_PENDING_CLIENT_REQUESTS) {
				future.completeExceptionally(new IllegalStateException(
					"Too many server config update requests are already pending."
				));
				return future;
			}
			requestId = getNextAvailableRequestId();
			pending = new PendingRequest(schema.getServerKey(), future, System.nanoTime());
			PENDING_REQUESTS.put(requestId, pending);
		}
		long registeredRequestId = requestId;
		PendingRequest registeredPending = pending;
		future.whenComplete((ignored, throwable) -> PENDING_REQUESTS.remove(registeredRequestId, registeredPending));
		try {
			ServerConfigUpdatePayload payload = new ServerConfigUpdatePayload(
				schema.getServerKey(),
				requestId,
				schema.serializeUpdates(updates)
			);
			if (ServerConfigNetworking.sendToServer(payload)) {
				return future;
			}
			failPending(
				requestId,
				pending,
				new IllegalStateException("The connected server does not support MezzConfig server updates.")
			);
		} catch (RuntimeException e) {
			failPending(requestId, pending, e);
		}
		return future;
	}

	public static CompletableFuture<Void> requestLocalUpdate(
		ConfigSchema schema,
		List<? extends ConfigValueUpdate<?>> updates
	) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		MinecraftServer server;
		synchronized (LOCAL_REQUEST_LOCK) {
			server = activeServer;
			if (server == null) {
				future.completeExceptionally(new IllegalStateException("There is no active local server."));
				return future;
			}
			PENDING_LOCAL_REQUESTS.add(future);
		}
		future.whenComplete((ignored, throwable) -> PENDING_LOCAL_REQUESTS.remove(future));
		Runnable task = () -> {
			try {
				if (activeServer != server) {
					throw new IllegalStateException("The local server stopped before the config update could be applied.");
				}
				schema.applyServerUpdates(updates);
				SERVER_SCHEMA_VERSIONS.put(schema, schema.getChangeVersion());
				broadcastSchema(server, schema, null, 0, true, "");
				future.complete(null);
			} catch (RuntimeException e) {
				future.completeExceptionally(e);
			}
		};
		if (server.isSameThread()) {
			task.run();
		} else {
			try {
				server.execute(task);
			} catch (RuntimeException e) {
				future.completeExceptionally(e);
			}
		}
		return future;
	}

	private static long getNextAvailableRequestId() {
		for (int i = 0; i <= MAX_PENDING_CLIENT_REQUESTS; i++) {
			long requestId = NEXT_REQUEST_ID.updateAndGet(ServerConfigRuntime::getNextRequestId);
			if (!PENDING_REQUESTS.containsKey(requestId)) {
				return requestId;
			}
		}
		throw new IllegalStateException("Unable to allocate a server config request id.");
	}

	private static long getNextRequestId(long current) {
		if (current == Long.MAX_VALUE) {
			return 1;
		}
		return current + 1;
	}

	public static void handleSync(ServerConfigSyncPayload payload) {
		PendingRequest pending = null;
		if (payload.requestId() != 0) {
			pending = PENDING_REQUESTS.get(payload.requestId());
		}
		try {
			if (pending != null && !pending.key().equals(payload.key())) {
				throw new IllegalArgumentException("Server config response key does not match the pending request.");
			}
			Optional<ConfigSchema> schema = getClientConfigManager()
				.flatMap(configManager -> configManager.getServerSchema(payload.key()));
			if (pending != null && payload.accepted() && schema.isEmpty()) {
				throw new IllegalStateException("The client no longer has the requested server config schema: " + payload.key());
			}
			if (schema.isPresent() && (payload.accepted() || !payload.values().isEmpty())) {
				schema.orElseThrow().applyRemoteSnapshot(payload.values(), payload.canEdit());
			}
			if (pending != null) {
				if (payload.accepted()) {
					completePending(payload.requestId(), pending);
				} else {
					failPending(payload.requestId(), pending, new IllegalStateException(payload.errorMessage()));
				}
			}
		} catch (RuntimeException e) {
			LOGGER.error("Failed to apply synchronized server config schema: {}", payload.key(), e);
			if (pending != null) {
				failPending(payload.requestId(), pending, e);
			}
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
		long nowNanos = System.nanoTime();
		SYNC_REASSEMBLER.expire(nowNanos);
		expireClientRequests(nowNanos);
	}

	static void expireClientRequests(long nowNanos) {
		for (Map.Entry<Long, PendingRequest> entry : PENDING_REQUESTS.entrySet()) {
			PendingRequest pending = entry.getValue();
			if (nowNanos - pending.createdNanos() >= CLIENT_REQUEST_TIMEOUT_NANOS) {
				failPending(
					entry.getKey(),
					pending,
					new IllegalStateException("Timed out waiting for server config update response for " + pending.key() + ".")
				);
			}
		}
	}

	public static void onClientDisconnect() {
		SYNC_REASSEMBLER.clear();
		getClientConfigManager().ifPresent(manager -> manager.getServerSchemas().forEach(ConfigSchema::clearRemoteSnapshot));
		IllegalStateException exception = new IllegalStateException("Disconnected before the server config update completed.");
		List<PendingRequest> pendingRequests;
		synchronized (PENDING_REQUESTS) {
			pendingRequests = List.copyOf(PENDING_REQUESTS.values());
			PENDING_REQUESTS.clear();
		}
		pendingRequests.forEach(pending -> pending.future().completeExceptionally(exception));
	}

	private static String getExceptionMessage(RuntimeException exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return exception.getClass().getSimpleName();
		}
		return summarizeErrorMessage(message);
	}

	private static String summarizeErrorMessage(String message) {
		if (message.length() <= MAX_ERROR_MESSAGE_CHARACTERS) {
			return message;
		}
		return message.substring(0, MAX_ERROR_MESSAGE_CHARACTERS - 1) + "…";
	}

	private static void completePending(long requestId, PendingRequest pending) {
		if (PENDING_REQUESTS.remove(requestId, pending)) {
			pending.future().complete(null);
		}
	}

	private static void failPending(long requestId, PendingRequest pending, RuntimeException exception) {
		if (PENDING_REQUESTS.remove(requestId, pending)) {
			pending.future().completeExceptionally(exception);
		}
	}

	private static Optional<ConfigManager> getServerConfigManager() {
		return Optional.ofNullable(serverConfigManager);
	}

	private static Optional<ConfigManager> getClientConfigManager() {
		return ConfigManagers.getConfigManager()
			.filter(ConfigManager.class::isInstance)
			.map(ConfigManager.class::cast);
	}

	private record PendingRequest(
		ServerConfigKey key,
		CompletableFuture<Void> future,
		long createdNanos
	) {}
}
