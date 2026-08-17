package net.mezzdev.config.file;

import net.mezzdev.config.api.IConfigManager;
import net.mezzdev.config.api.schema.ConfigOwnership;
import net.mezzdev.config.api.schema.ConfigScope;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.server.ServerConfigKey;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.deduplicatingrunner.DelayedExecutor;
import net.mezzdev.deduplicatingrunner.DelayedTaskScheduler;
import net.mezzdev.filewatcher.FileWatcher;
import net.mezzdev.filewatcher.FileWatcherUnavailableException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ConfigManager implements IConfigManager {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Duration SAVE_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
	private static final String SAVE_SCHEDULER_THREAD_NAME = "MezzConfig Save Scheduler";

	private final @Nullable FileWatcher fileWatcher;
	private final DelayedExecutor saveExecutor;
	private final List<ConfigSchema> schemas = new ArrayList<>();
	private final Map<RegistrationKey, ConfigSchema> schemasByKey = new LinkedHashMap<>();
	private final Set<RegistrationKey> reservedKeys = new HashSet<>();
	private final boolean logUntranslatedKeys;

	public ConfigManager() {
		this("Config File Watcher");
	}

	public ConfigManager(String fileWatcherThreadName) {
		this(fileWatcherThreadName, ConfigFileWatcherSettings.defaults());
	}

	public ConfigManager(String fileWatcherThreadName, ConfigFileWatcherSettings fileWatcherSettings) {
		this(fileWatcherThreadName, fileWatcherSettings, false);
	}

	public ConfigManager(
		String fileWatcherThreadName,
		ConfigFileWatcherSettings fileWatcherSettings,
		boolean logUntranslatedKeys
	) {
		fileWatcherThreadName = ErrorUtil.checkNotNull(fileWatcherThreadName, "fileWatcherThreadName");
		fileWatcherSettings = ErrorUtil.checkNotNull(fileWatcherSettings, "fileWatcherSettings");
		this.fileWatcher = createFileWatcher(fileWatcherThreadName, fileWatcherSettings);
		this.logUntranslatedKeys = logUntranslatedKeys;
		this.saveExecutor = new DelayedExecutor(SAVE_SHUTDOWN_TIMEOUT, SAVE_SCHEDULER_THREAD_NAME);
		Runtime.getRuntime()
			.addShutdownHook(new Thread(saveExecutor::shutdown, SAVE_SCHEDULER_THREAD_NAME + " Shutdown"));
	}

	private static @Nullable FileWatcher createFileWatcher(
		String fileWatcherThreadName,
		ConfigFileWatcherSettings fileWatcherSettings
	) {
		if (!fileWatcherSettings.enabled()) {
			LOGGER.info("Automatic config file watching is disabled.");
			return null;
		}
		try {
			return new FileWatcher(
				fileWatcherThreadName,
				fileWatcherSettings.changeSettlingDelay(),
				fileWatcherSettings.missingDirectoryRetryInterval()
			);
		} catch (FileWatcherUnavailableException e) {
			LOGGER.error("Automatic config file watching is unavailable.", e);
			return null;
		}
	}

	public DelayedTaskScheduler getSaveScheduler() {
		return saveExecutor;
	}

	public void registerSchema(ConfigSchema schema) {
		RegistrationKey key = reserve(schema);
		boolean initialized = false;
		try {
			schema.register(fileWatcher, logUntranslatedKeys);
			initialized = true;
			publish(key, schema);
		} catch (RuntimeException | Error e) {
			cancelReservation(key);
			if (initialized) {
				schema.rollbackRegistration(e);
			}
			throw e;
		}
		if (schema.getOwnership() == ConfigOwnership.SERVER && schema.getScope() == ConfigScope.WORLD) {
			try {
				ServerConfigRuntime.onServerSchemaRegistered(schema);
			} catch (RuntimeException e) {
				LOGGER.error("Failed to synchronize newly registered server config schema: {}", schema.getServerKey(), e);
			}
		}
	}

	private synchronized RegistrationKey reserve(ConfigSchema schema) {
		RegistrationKey key = getRegistrationKey(schema);
		if (schemasByKey.containsKey(key) || !reservedKeys.add(key)) {
			throw new IllegalArgumentException("There is already a config schema registered for: " + key);
		}
		return key;
	}

	private synchronized void cancelReservation(RegistrationKey key) {
		reservedKeys.remove(key);
	}

	private synchronized void publish(RegistrationKey key, ConfigSchema schema) {
		if (!reservedKeys.remove(key)) {
			throw new IllegalStateException("Config schema identity was not reserved: " + key);
		}
		if (schemasByKey.putIfAbsent(key, schema) != null) {
			throw new IllegalStateException("Config schema identity was published while reserved: " + key);
		}
		schemas.add(schema);
	}

	private static RegistrationKey getRegistrationKey(ConfigSchema schema) {
		if (schema.getOwnership() == ConfigOwnership.SERVER && schema.getScope() == ConfigScope.WORLD) {
			return new RegistrationKey(schema.getOwnership(), schema.getScope(), schema.getServerKey());
		}
		if (schema.getScope() == ConfigScope.INSTALLATION) {
			Path path = schema.getRegistrationPath()
				.orElseThrow(() -> new IllegalArgumentException("Installation-scoped schemas must have a backing file."))
				.toAbsolutePath()
				.normalize();
			return new RegistrationKey(schema.getOwnership(), schema.getScope(), path);
		}
		if (schema.getOwnership() == ConfigOwnership.CLIENT) {
			Path path = schema.getDefaultPath()
				.orElseThrow(() -> new IllegalArgumentException("Client-owned world schemas must have a default file."))
				.toAbsolutePath()
				.normalize();
			return new RegistrationKey(schema.getOwnership(), schema.getScope(), path);
		}
		throw new IllegalArgumentException(
			"Unsupported config schema ownership and scope: " + schema.getOwnership() + ", " + schema.getScope()
		);
	}

	public void startWatching() {
		if (fileWatcher != null) {
			fileWatcher.start();
		}
	}

	public void onWorldStarted() {
		getConfigSchemaSnapshot().forEach(ConfigSchema::promotePendingValuesAfterWorldRestart);
	}

	@Override
	public Collection<? extends IConfigSchema> getSchemas() {
		return getConfigSchemaSnapshot();
	}

	public Collection<ConfigSchema> getServerSchemas() {
		return getConfigSchemaSnapshot().stream()
			.filter(schema -> schema.getOwnership() == ConfigOwnership.SERVER)
			.filter(schema -> schema.getScope() == ConfigScope.WORLD)
			.toList();
	}

	public synchronized Optional<ConfigSchema> getServerSchema(ServerConfigKey key) {
		RegistrationKey registrationKey = new RegistrationKey(
			ConfigOwnership.SERVER,
			ConfigScope.WORLD,
			key
		);
		return Optional.ofNullable(schemasByKey.get(registrationKey));
	}

	private synchronized List<ConfigSchema> getConfigSchemaSnapshot() {
		return List.copyOf(schemas);
	}

	private record RegistrationKey(ConfigOwnership ownership, ConfigScope scope, Object identity) {}
}
