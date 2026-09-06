package net.mezzdev.config.server;

import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.util.ErrorUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

final class ServerConfigClientConnection {
	private static final Logger LOGGER = LogManager.getLogger();

	private final Supplier<ConfigManager> configManagerSupplier;
	private final ServerConfigPayloadReassembler syncReassembler = new ServerConfigPayloadReassembler();
	private final AtomicReference<UUID> remoteServerId = new AtomicReference<>();

	ServerConfigClientConnection(Supplier<ConfigManager> configManagerSupplier) {
		this.configManagerSupplier = ErrorUtil.checkNotNull(configManagerSupplier, "configManagerSupplier");
	}

	Optional<UUID> getRemoteServerId() {
		return Optional.ofNullable(remoteServerId.get());
	}

	void onWorldStarted() {
		getConfigManager().onWorldStarted();
	}

	void handleServerIdentity(ServerIdentityPayload payload) {
		UUID serverId = payload.serverId();
		if (remoteServerId.compareAndSet(null, serverId)) {
			getConfigManager().onClientServerIdentityReceived();
			return;
		}
		UUID currentServerId = remoteServerId.get();
		if (!serverId.equals(currentServerId)) {
			LOGGER.warn("Ignored a conflicting server identity for the current connection.");
		}
	}

	void handleSync(ServerConfigSyncPayload payload) {
		try {
			getConfigManager().getServerSchema(payload.key())
				.ifPresent(schema -> schema.applyRemoteSnapshot(payload.values()));
		} catch (RuntimeException e) {
			LOGGER.error("Failed to apply synchronized server config schema: {}", payload.key(), e);
		}
	}

	void handleSyncChunk(ServerConfigSyncChunkPayload chunk) {
		try {
			syncReassembler.accept(chunk.payloadInternal())
				.map(ServerConfigPayloadCodec::decodeSync)
				.ifPresent(this::handleSync);
		} catch (RuntimeException e) {
			LOGGER.warn("Rejected malformed synchronized server config fragment: {}", getExceptionMessage(e));
			LOGGER.debug("Malformed synchronized server config fragment details.", e);
		}
	}

	void onTick() {
		getConfigManager().logUntranslatedKeysIfReady();
		syncReassembler.expire(System.nanoTime());
	}

	void onDisconnect() {
		syncReassembler.clear();
		remoteServerId.set(null);
		getConfigManager().getServerSchemas().forEach(ConfigSchema::clearRemoteSnapshot);
	}

	private ConfigManager getConfigManager() {
		return configManagerSupplier.get();
	}

	private static String getExceptionMessage(RuntimeException exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return exception.getClass().getSimpleName();
		}
		return message;
	}
}
