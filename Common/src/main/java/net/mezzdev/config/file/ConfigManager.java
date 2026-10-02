package net.mezzdev.config.file;

import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigSchemaDefinition;
import net.mezzdev.config.server.ServerConfigKey;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.serializers.StringSerializer;
import net.mezzdev.config.sorting.SortingConfig;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ConfigManager {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Duration SAVE_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
	private static final String SAVE_SCHEDULER_THREAD_NAME = "MezzConfig Save Scheduler";

	private final FileWatcherRegistration clientFileWatcher;
	private final FileWatcherRegistration serverFileWatcher;
	private final DelayedExecutor saveExecutor;
	private final List<ConfigSchema> schemas = new ArrayList<>();
	private final Map<RegistrationKey, ConfigSchema> schemasByKey = new LinkedHashMap<>();
	private final Set<RegistrationKey> reservedKeys = new HashSet<>();
	private final ConfigPathReservations pathReservations = new ConfigPathReservations();
	private final boolean logUntranslatedKeys;

	public ConfigManager() {
		this("Config File Watcher");
	}

	public ConfigManager(String fileWatcherThreadName) {
		this(
			fileWatcherThreadName,
			ConfigFileWatcherSettings.clientDefaults(),
			ConfigFileWatcherSettings.serverDefaults()
		);
	}

	public ConfigManager(
		String fileWatcherThreadName,
		ConfigFileWatcherSettings clientFileWatcherSettings,
		ConfigFileWatcherSettings serverFileWatcherSettings
	) {
		this(fileWatcherThreadName, clientFileWatcherSettings, serverFileWatcherSettings, false);
	}

	public ConfigManager(
		String fileWatcherThreadName,
		ConfigFileWatcherSettings clientFileWatcherSettings,
		ConfigFileWatcherSettings serverFileWatcherSettings,
		boolean logUntranslatedKeys
	) {
		this(
			fileWatcherThreadName,
			clientFileWatcherSettings,
			serverFileWatcherSettings,
			logUntranslatedKeys,
			createSaveExecutor()
		);
	}

	ConfigManager(
		String fileWatcherThreadName,
		ConfigFileWatcherSettings clientFileWatcherSettings,
		ConfigFileWatcherSettings serverFileWatcherSettings,
		boolean logUntranslatedKeys,
		DelayedExecutor saveExecutor
	) {
		fileWatcherThreadName = ErrorUtil.checkNotNull(fileWatcherThreadName, "fileWatcherThreadName");
		clientFileWatcherSettings = ErrorUtil.checkNotNull(clientFileWatcherSettings, "clientFileWatcherSettings");
		serverFileWatcherSettings = ErrorUtil.checkNotNull(serverFileWatcherSettings, "serverFileWatcherSettings");
		this.saveExecutor = ErrorUtil.checkNotNull(saveExecutor, "saveExecutor");
		this.clientFileWatcher = new FileWatcherRegistration(
			fileWatcherThreadName,
			clientFileWatcherSettings,
			"client"
		);
		this.serverFileWatcher = new FileWatcherRegistration(
			fileWatcherThreadName,
			serverFileWatcherSettings,
			"server"
		);
		this.logUntranslatedKeys = logUntranslatedKeys;
		Runtime.getRuntime()
			.addShutdownHook(new Thread(saveExecutor::shutdown, SAVE_SCHEDULER_THREAD_NAME + " Shutdown"));
	}

	static DelayedExecutor createSaveExecutor() {
		return new DelayedExecutor(SAVE_SHUTDOWN_TIMEOUT, SAVE_SCHEDULER_THREAD_NAME);
	}

	public DelayedTaskScheduler getSaveScheduler() {
		return saveExecutor;
	}

	public ConfigSchema registerSchema(ConfigSchemaDefinition definition) {
		RegistrationKey key = reserve(definition);
		ConfigSchema schema;
		try {
			FileWatcher fileWatcher = getFileWatcher(definition.getType());
			schema = definition.initialize(
				fileWatcher,
				logUntranslatedKeys,
				paths -> pathReservations.replace(definition, getSchemaDescription(definition), paths),
				initializedSchema -> publish(key, initializedSchema)
			);
		} catch (RuntimeException | Error e) {
			cancelReservation(key);
			throw e;
		}
		schema.getServerSynchronization().ifPresent(synchronization -> {
			try {
				ServerConfigRuntime.onServerSchemaRegistered(synchronization);
			} catch (RuntimeException e) {
				LOGGER.error("Failed to synchronize newly registered server config schema: {}", synchronization.getKey(), e);
			}
		});
		return schema;
	}

	public <T> SortingConfig<T> createSortingConfig(
		Path path,
		IConfigValueSerializer<T> serializer,
		Comparator<T> defaultSortOrder,
		boolean allowsRemovingValues
	) {
		SortingConfig<T> sortingConfig = new SortingConfig<>(path, serializer, defaultSortOrder, allowsRemovingValues);
		pathReservations.replace(sortingConfig, "a sorting config", List.of(path));
		return sortingConfig;
	}

	public SortingConfig<String> createSortingConfig(
		Path path,
		Comparator<String> defaultSortOrder,
		boolean allowsRemovingValues
	) {
		return createSortingConfig(path, StringSerializer.INSTANCE, defaultSortOrder, allowsRemovingValues);
	}

	public <T> SortingConfig<T> createInMemorySortingConfig(
		IConfigValueSerializer<T> serializer,
		Comparator<T> defaultSortOrder,
		boolean allowsRemovingValues
	) {
		return SortingConfig.inMemory(serializer, defaultSortOrder, allowsRemovingValues);
	}

	public SortingConfig<String> createInMemorySortingConfig(
		Comparator<String> defaultSortOrder,
		boolean allowsRemovingValues
	) {
		return createInMemorySortingConfig(StringSerializer.INSTANCE, defaultSortOrder, allowsRemovingValues);
	}

	private static String getSchemaDescription(ConfigSchemaDefinition definition) {
		return "a %s config schema for mod '%s'".formatted(
			definition.getType().name().toLowerCase(Locale.ROOT),
			definition.getModId()
		);
	}

	private @Nullable FileWatcher getFileWatcher(ConfigSchemaType type) {
		if (type == ConfigSchemaType.SERVER) {
			return serverFileWatcher.getOrCreate();
		}
		return clientFileWatcher.getOrCreate();
	}

	private synchronized RegistrationKey reserve(ConfigSchemaDefinition definition) {
		RegistrationKey key = getRegistrationKey(definition);
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

	private static RegistrationKey getRegistrationKey(ConfigSchemaDefinition definition) {
		if (definition.getType() == ConfigSchemaType.SERVER) {
			return new RegistrationKey(definition.getType(), definition.getServerKey());
		}
		if (definition.getType() == ConfigSchemaType.CLIENT) {
			Path path = definition.getRegistrationPath()
				.orElseThrow(() -> new IllegalArgumentException("Client schemas must have a backing file."))
				.toAbsolutePath()
				.normalize();
			return new RegistrationKey(definition.getType(), path);
		}
		if (definition.getType() == ConfigSchemaType.CLIENT_PER_WORLD) {
			Path path = definition.getDefaultPath()
				.orElseThrow(() -> new IllegalArgumentException("Client-world schemas must have a default file."))
				.toAbsolutePath()
				.normalize();
			return new RegistrationKey(definition.getType(), path);
		}
		throw new IllegalArgumentException("Unsupported config schema type: " + definition.getType());
	}

	public void startWatching() {
		clientFileWatcher.start();
		serverFileWatcher.start();
	}

	public void onWorldStarted() {
		List<ConfigSchema> schemas = getConfigSchemaSnapshot();
		// Loading one schema can notify a listener that reads another schema.
		// Mark all paths dirty first so those reads use the new world's files.
		schemas.forEach(ConfigSchema::invalidatePaths);
		schemas.forEach(ConfigSchema::promotePendingValuesAfterWorldRestart);
	}

	public void onClientServerIdentityReceived() {
		List<ConfigSchema> schemas = getClientWorldSchemas();
		schemas.forEach(ConfigSchema::invalidatePaths);
		schemas.forEach(ConfigSchema::promotePendingValuesAfterWorldRestart);
	}

	public void onClientWorldStopped() {
		List<ConfigSchema> schemas = getClientWorldSchemas();
		schemas.forEach(ConfigSchema::invalidatePaths);
		schemas.forEach(ConfigSchema::loadIfNeeded);
	}

	public void onServerStopped() {
		Collection<ConfigSchema.ServerSynchronization> synchronizations = getServerSynchronizations();
		synchronizations.forEach(synchronization -> synchronization.getSchema().invalidatePaths());
		synchronizations.forEach(synchronization -> {
			synchronization.clearRemoteSnapshot();
			synchronization.getSchema().loadIfNeeded();
		});
	}

	public void logUntranslatedKeysIfReady() {
		if (logUntranslatedKeys) {
			getConfigSchemaSnapshot().forEach(ConfigSchema::logUntranslatedKeysIfReady);
		}
	}

	public Collection<? extends IConfigSchema> getSchemas() {
		return getConfigSchemaSnapshot();
	}

	public Collection<ConfigSchema.ServerSynchronization> getServerSynchronizations() {
		return getConfigSchemaSnapshot().stream()
			.flatMap(schema -> schema.getServerSynchronization().stream())
			.toList();
	}

	private List<ConfigSchema> getClientWorldSchemas() {
		return getConfigSchemaSnapshot().stream()
			.filter(schema -> schema.getType() == ConfigSchemaType.CLIENT_PER_WORLD)
			.toList();
	}

	public Optional<ConfigSchema.ServerSynchronization> getServerSynchronization(ServerConfigKey key) {
		RegistrationKey registrationKey = new RegistrationKey(ConfigSchemaType.SERVER, key);
		ConfigSchema schema;
		synchronized (this) {
			schema = schemasByKey.get(registrationKey);
		}
		return Optional.ofNullable(schema).flatMap(ConfigSchema::getServerSynchronization);
	}

	private synchronized List<ConfigSchema> getConfigSchemaSnapshot() {
		return List.copyOf(schemas);
	}

	private record RegistrationKey(ConfigSchemaType type, Object identity) {}

	private static final class FileWatcherRegistration {
		private final String threadName;
		private final ConfigFileWatcherSettings settings;
		private final String ownershipName;
		private boolean initialized;
		private boolean startRequested;
		private @Nullable FileWatcher fileWatcher;

		private FileWatcherRegistration(
			String threadName,
			ConfigFileWatcherSettings settings,
			String ownershipName
		) {
			this.threadName = threadName;
			this.settings = settings;
			this.ownershipName = ownershipName;
		}

		private synchronized @Nullable FileWatcher getOrCreate() {
			if (!initialized) {
				initialized = true;
				fileWatcher = createFileWatcher();
				if (startRequested && fileWatcher != null) {
					fileWatcher.start();
				}
			}
			return fileWatcher;
		}

		private synchronized void start() {
			if (startRequested) {
				return;
			}
			startRequested = true;
			if (fileWatcher != null) {
				fileWatcher.start();
			}
		}

		private @Nullable FileWatcher createFileWatcher() {
			if (!settings.enabled()) {
				LOGGER.info("Automatic {} config file watching is disabled.", ownershipName);
				return null;
			}
			try {
				return new FileWatcher(
					threadName + " " + ownershipName,
					settings.changeSettlingDelay(),
					settings.missingDirectoryRetryInterval()
				);
			} catch (FileWatcherUnavailableException e) {
				LOGGER.error("Automatic {} config file watching is unavailable.", ownershipName, e);
				return null;
			}
		}
	}
}
