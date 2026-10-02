package net.mezzdev.config.schema;

import com.google.gson.JsonElement;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.update.IConfigBatchUpdater;
import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.migration.ConfigMigrationStatus;
import net.mezzdev.config.api.value.change.IAppliedConfigValueChange;
import net.mezzdev.config.api.value.serializer.IDeserializeResult;
import net.mezzdev.config.api.value.editor.ConfigValueRestartRequirement;
import net.mezzdev.config.api.value.change.IConfigValueBatchChangeListener;
import net.mezzdev.config.file.ConfigFileReader;
import net.mezzdev.config.file.ConfigFileValueAdapter;
import net.mezzdev.config.file.ConfigFileValueCodec;
import net.mezzdev.config.file.ConfigFileTransaction;
import net.mezzdev.config.file.ConfigFileUtil;
import net.mezzdev.config.file.ConfigSerializer;
import net.mezzdev.config.migration.ConfigMigrationResult;
import net.mezzdev.config.server.ServerConfigKey;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.server.ServerConfigValueData;
import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.config.util.ListenerList;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.value.ConfigValueOwner;
import net.mezzdev.config.value.AppliedConfigValueChange;
import net.mezzdev.config.value.ConfigValueUpdate;
import net.mezzdev.config.sorting.SortingConfig;
import net.mezzdev.deduplicatingrunner.DeduplicatingRunner;
import net.mezzdev.filewatcher.FileWatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class ConfigSchema implements IConfigSchema, ConfigValueOwner {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Duration SAVE_DELAY_TIME = Duration.ofSeconds(2);
	private static final int LOCALIZATION_SAVE_RETRY_LIMIT = 30;

	private final ConfigSchemaDefinition definition;
	private final ConfigSchemaPathResolver pathResolver;
	private final EnumSet<PendingLoad> pendingLoad = EnumSet.allOf(PendingLoad.class);
	private final AtomicLong changeVersion = new AtomicLong();
	private final DeduplicatingRunner delayedSave;
	private final @Nullable FileWatcher fileWatcher;
	private ActiveFiles activeFiles = ActiveFiles.NONE;
	private @Nullable Path pendingSavePath;
	private final Set<Path> pendingFileChanges = new HashSet<>();
	private final @Nullable ServerSynchronization serverSynchronization;
	private boolean restartValuesInitialized;
	private final boolean logUntranslatedKeys;
	private boolean translationKeysChecked;
	private volatile boolean remotelyActive;
	private boolean migrationCompleted;
	private final Consumer<? super Collection<Path>> pathReservation;

	private ConfigSchema(
		ConfigSchemaDefinition definition,
		ConfigSchemaPathResolver pathResolver,
		@Nullable FileWatcher fileWatcher,
		boolean logUntranslatedKeys,
		Consumer<? super Collection<Path>> pathReservation
	) {
		this.definition = definition;
		this.pathResolver = pathResolver;
		this.fileWatcher = fileWatcher;
		this.logUntranslatedKeys = logUntranslatedKeys;
		this.pathReservation = pathReservation;
		this.delayedSave = new DeduplicatingRunner(SAVE_DELAY_TIME, definition.scheduler);
		if (definition.serverKey == null) {
			this.serverSynchronization = null;
		} else {
			this.serverSynchronization = new ServerSynchronization(definition.serverKey);
		}
	}

	static ConfigSchema create(
		ConfigSchemaDefinition definition,
		@Nullable FileWatcher fileWatcher,
		boolean logUntranslatedKeys,
		Consumer<? super Collection<Path>> pathReservation,
		Consumer<ConfigSchema> publish
	) {
		ErrorUtil.checkNotNull(pathReservation, "pathReservation");
		ErrorUtil.checkNotNull(publish, "publish");
		ConfigSchema schema = new ConfigSchema(definition, definition.pathResolver, fileWatcher, logUntranslatedKeys, pathReservation);
		synchronized (schema) {
			try {
				pathReservation.accept(definition.pathResolver.getPersistentReservationPaths());
				schema.loadInitialValues();
				publish.accept(schema);
				return schema;
			} catch (RuntimeException | Error e) {
				schema.closeFailedInitialization(e);
				definition.resetValues();
				throw e;
			}
		}
	}

	static ConfigSchema createInactive(ConfigSchemaDefinition definition) {
		ConfigSchema schema = new ConfigSchema(definition, Optional::empty, null, false, paths -> {});
		schema.pendingLoad.clear();
		definition.bindValues(schema);
		schema.completeInactiveMigration();
		return schema;
	}

	public synchronized void loadIfNeeded() {
		if (pendingLoad.isEmpty()) {
			return;
		}
		loadFiles().ifPresent(loaded -> finishLoad(validateReload(loaded)));
	}

	private void loadInitialValues() {
		loadFiles().ifPresent(loaded -> {
			if (isSynchronizedServerSchema()) {
				validateCurrentServerSnapshot();
			}
			LoadResult result = createLoadResult(loaded.previousState(), loaded.initialSaves());
			saveInitialFiles(result.initialSaves());
			definition.bindValues(this);
			notifyChanges(result.effectiveChanges(), result.pendingChanges());
		});
	}

	private void finishLoad(LoadResult loadResult) {
		notifyChanges(loadResult.effectiveChanges(), loadResult.pendingChanges());
		saveInitialFiles(loadResult.initialSaves());
	}

	private void saveInitialFiles(List<InitialSave> initialSaves) {
		for (InitialSave initialSave : initialSaves) {
			if (definition.mode.synchronousFileAccess()) {
				saveInitialFile(initialSave);
			} else {
				saveInitialFileAfterLocalizationLoads(initialSave, 0);
			}
		}
	}

	private Optional<FileLoadResult> loadFiles() {
		Path previousDefaultPath = activeFiles.defaultPath();
		Path previousPath = activeFiles.path();
		Path defaultPath = previousDefaultPath;
		Path path = previousPath;
		if (pendingLoad.contains(PendingLoad.CHECK_PATHS)) {
			defaultPath = pathResolver.resolveDefaultPath().map(Path::normalize).orElse(null);
			path = pathResolver.resolvePath().map(Path::normalize).orElse(null);
		}
		boolean activePathChanged = !Objects.equals(path, previousPath);
		boolean pathsChanged = activePathChanged || !Objects.equals(defaultPath, previousDefaultPath);
		boolean reloadValues = pathsChanged || pendingLoad.contains(PendingLoad.RELOAD_VALUES);
		boolean switchingFromRemoteValues = remotelyActive && path != null;
		if (!reloadValues && !switchingFromRemoteValues) {
			pendingLoad.clear();
			return Optional.empty();
		}

		LoadState previousState = new LoadState(
			getEffectiveValues(), getPendingValues(), restartValuesInitialized, remotelyActive, usesDeclaredDefaults()
		);
		if (pathsChanged) {
			transitionActivePaths(defaultPath, path, previousDefaultPath, previousPath);
		}
		pendingLoad.clear();
		if (isSynchronizedServerSchema() && remotelyActive && path == null) {
			pendingFileChanges.clear();
			return Optional.of(new FileLoadResult(previousState, List.of()));
		}
		if (path != null) {
			remotelyActive = false;
		}

		if (path == null) {
			pendingFileChanges.clear();
			List<InitialSave> initialSaves = getInitialSaves(defaultPath, null, activePathChanged, false, true, null);
			if (previousPath != null) {
				resetValuesToDefaults();
			}
			return Optional.of(new FileLoadResult(previousState, initialSaves));
		}

		if (!reloadValues) {
			return Optional.of(new FileLoadResult(previousState, List.of()));
		}
		Map<Path, ConfigFileReader.Contents> preparedFileLoads = new LinkedHashMap<>();
		if (!pendingFileChanges.isEmpty()) {
			boolean allFilesUnchanged = true;
			for (Path changedPath : pendingFileChanges) {
				try {
					ConfigFileReader.Contents contents = ConfigFileReader.read(changedPath);
					preparedFileLoads.put(changedPath, contents);
					allFilesUnchanged &= ConfigSerializer.isFileUnchangedSinceLastSave(changedPath, contents.fingerprint());
				} catch (IOException | ConfigFileReader.MalformedFileException e) {
					ConfigSerializer.clearLastSavedFileFingerprint(changedPath);
					allFilesUnchanged = false;
				}
			}
			pendingFileChanges.clear();
			if (allFilesUnchanged) {
				return Optional.of(new FileLoadResult(previousState, List.of()));
			}
		}

		resetValuesToDefaults();
		load(defaultPath, preparedFileLoads);
		MigrationAttempt migrationAttempt = attemptMigration(path);
		String loadedFingerprint = null;
		if (migrationAttempt != MigrationAttempt.MIGRATED) {
			loadedFingerprint = load(path, preparedFileLoads);
		}
		if (!restartValuesInitialized) {
			promotePendingValuesWithoutNotifying(ConfigValueRestartRequirement.GAME_RESTART);
			restartValuesInitialized = true;
		}
		return Optional.of(new FileLoadResult(
			previousState,
			getInitialSaves(
				defaultPath,
				path,
				activePathChanged,
				isSynchronizedServerSchema(),
				migrationAttempt != MigrationAttempt.FAILED,
				loadedFingerprint
			)
		));
	}

	/**
	 * Call this when the config file paths may have changed, such as after joining or leaving a world.
	 * If the paths are unchanged, checking them does not reload the files or discard unsaved edits.
	 */
	public synchronized void invalidatePaths() {
		pendingLoad.add(PendingLoad.CHECK_PATHS);
	}

	private MigrationAttempt attemptMigration(Path destinationPath) {
		ConfigMigrationSpec migrationSpec = definition.migrationSpec;
		if (migrationSpec == null || migrationCompleted) {
			return MigrationAttempt.NOT_ATTEMPTED;
		}
		ConfigMigrationResult result = runMigration(destinationPath);
		completeMigration(result);
		return switch (result.getStatus()) {
			case MIGRATED -> MigrationAttempt.MIGRATED;
			case FAILED -> MigrationAttempt.FAILED;
			default -> MigrationAttempt.SKIPPED;
		};
	}

	private ConfigMigrationResult runMigration(Path destinationPath) {
		ConfigMigrationSpec migrationSpec = definition.migrationSpec;
		if (migrationSpec == null) {
			throw new IllegalStateException("Config schema has no registered legacy source or migration.");
		}
		destinationPath = destinationPath.toAbsolutePath().normalize();
		Path legacyPath = null;
		Path legacyBackupPath = null;
		ConfigMigrationContext context = null;
		try {
			if (Files.exists(destinationPath)) {
				return new ConfigMigrationResult(
					ConfigMigrationStatus.SKIPPED_DESTINATION_EXISTS,
					destinationPath,
					null,
					null,
					null
				);
			}

			legacyPath = migrationSpec.legacyPaths()
				.stream()
				.filter(Files::exists)
				.findFirst()
				.orElse(null);
			if (legacyPath == null) {
				return new ConfigMigrationResult(
					ConfigMigrationStatus.SKIPPED_NO_LEGACY_FILE,
					destinationPath,
					null,
					null,
					null
				);
			}

			legacyBackupPath = ConfigFileUtil.backUpFile(legacyPath);
			context = new ConfigMigrationContext(this);
			try {
				if (migrationSpec.loadsAlternateSource()) {
					context.addParseResult(ConfigSerializer.parseMigrationUpdates(legacyPath, definition.categories));
				} else {
					migrationSpec.migrate(legacyPath, context);
				}
			} finally {
				context.close();
			}
			if (!context.hasUpdates() && !context.getDiagnostics().isEmpty()) {
				throw new IllegalArgumentException("Legacy config parsing produced no usable updates; see migration diagnostics.");
			}
			commitMigration(destinationPath, legacyBackupPath, context);
			ConfigMigrationResult result = new ConfigMigrationResult(
				ConfigMigrationStatus.MIGRATED,
				destinationPath,
				legacyPath,
				legacyBackupPath,
				context.getValueUpdates().size(),
				context.getRejectedValueCount(),
				context.getDiagnostics(),
				null
			);
			LOGGER.info(
				"Migrated legacy config file '{}' to '{}'; the source was preserved and backed up at '{}'.",
				legacyPath,
				destinationPath,
				legacyBackupPath
			);
			return result;
		} catch (Exception failure) {
			if (failure instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			int rejectedValueCount = 0;
			List<String> diagnostics = List.of();
			if (context != null) {
				rejectedValueCount = context.getRejectedValueCount();
				diagnostics = context.getDiagnostics();
			}
			ConfigMigrationResult result = new ConfigMigrationResult(
				ConfigMigrationStatus.FAILED,
				destinationPath,
				legacyPath,
				legacyBackupPath,
				0,
				rejectedValueCount,
				diagnostics,
				failure
			);
			LOGGER.error("Failed to migrate legacy config file '{}' to '{}'; no migration updates were applied and the source was preserved.", legacyPath, destinationPath, failure);
			return result;
		}
	}

	private void completeMigration(ConfigMigrationResult result) {
		migrationCompleted = true;
		try {
			definition.migrationSpec.onMigrationComplete(result);
		} catch (RuntimeException callbackFailure) {
			LOGGER.error("Failed to handle the completed legacy config migration for '{}'.", definition.id, callbackFailure);
		}
	}

	private void commitMigration(
		Path destinationPath,
		Path legacyBackupPath,
		ConfigMigrationContext context
	) throws IOException {
		List<ConfigValueUpdate<?>> valueUpdates = context.getValueUpdates();
		validateUpdates(valueUpdates);
		Map<ConfigValue<?>, Object> updatedValues = getUpdatedValues(valueUpdates);
		List<String> serializedSchema = ConfigSerializer.serializePendingSave(
			definition.categories,
			definition.mode.serializationSettings(),
			updatedValues
		);
		validateProspectiveServerSnapshot(updatedValues);

		List<SortingConfig.MigrationUpdate<?>> sortingUpdates = context.getSortingUpdates();
		Map<Path, List<String>> outputs = new LinkedHashMap<>();
		putMigrationOutput(outputs, destinationPath, serializedSchema);
		for (SortingConfig.MigrationUpdate<?> sortingUpdate : sortingUpdates) {
			putMigrationOutput(outputs, sortingUpdate.path(), sortingUpdate.serialized());
		}
		for (Path legacyPath : definition.migrationSpec.legacyPaths()) {
			if (outputs.containsKey(legacyPath)) {
				throw new IllegalArgumentException("Migration updates must not overwrite a registered legacy file: " + legacyPath);
			}
		}
		if (outputs.containsKey(legacyBackupPath)) {
			throw new IllegalArgumentException("Migration updates must not overwrite the legacy file backup: " + legacyBackupPath);
		}
		Map<ConfigValue<?>, Object> previousEffectiveValues = getEffectiveValues();
		Map<ConfigValue<?>, Object> previousPendingValues = getPendingValues();
		List<SortingConfig.MigrationUpdate<?>> appliedSortingUpdates = new ArrayList<>();
		boolean schemaUpdatesApplied = false;
		try {
			applyUpdatesAtomically(valueUpdates);
			schemaUpdatesApplied = true;
			for (SortingConfig.MigrationUpdate<?> sortingUpdate : sortingUpdates) {
				sortingUpdate.apply();
				appliedSortingUpdates.add(sortingUpdate);
			}
			ConfigFileTransaction.write(outputs, Set.of(destinationPath));
		} catch (IOException | RuntimeException | Error failure) {
			for (int index = appliedSortingUpdates.size() - 1; index >= 0; index--) {
				try {
					appliedSortingUpdates.get(index).rollback();
				} catch (RuntimeException | Error rollbackFailure) {
					failure.addSuppressed(rollbackFailure);
				}
			}
			if (schemaUpdatesApplied) {
				try {
					restoreValues(previousEffectiveValues, previousPendingValues);
				} catch (RuntimeException | Error rollbackFailure) {
					failure.addSuppressed(rollbackFailure);
				}
			}
			throw failure;
		}

		for (SortingConfig.MigrationUpdate<?> sortingUpdate : sortingUpdates) {
			sortingUpdate.notifyIfChanged();
		}
	}

	private static void putMigrationOutput(Map<Path, List<String>> outputs, Path path, List<String> serialized) {
		path = path.toAbsolutePath().normalize();
		if (outputs.putIfAbsent(path, serialized) != null) {
			throw new IllegalArgumentException("Migration cannot update the same destination more than once: " + path);
		}
	}

	private void completeInactiveMigration() {
		if (definition.migrationSpec != null && !migrationCompleted) {
			completeMigration(new ConfigMigrationResult(
				ConfigMigrationStatus.SKIPPED_INACTIVE,
				null,
				null,
				null,
				null
			));
		}
	}

	private LoadResult validateReload(FileLoadResult loaded) {
		LoadState previousState = loaded.previousState();
		List<InitialSave> initialSaves = loaded.initialSaves();
		if (!isSynchronizedServerSchema()) {
			return createLoadResult(previousState, initialSaves);
		}
		try {
			validateCurrentServerSnapshot();
			return createLoadResult(previousState, initialSaves);
		} catch (RuntimeException e) {
			restoreValues(previousState.effectiveValues(), previousState.pendingValues());
			restartValuesInitialized = previousState.restartValuesInitialized();
			remotelyActive = previousState.remotelyActive();
			LOGGER.error(
				"Rejected unsynchronizable server config file state for '{}'; keeping the previous authoritative values.",
				activeFiles.path(),
				e
			);
			return createLoadResult(previousState, List.of());
		}
	}

	private LoadResult createLoadResult(
		LoadState previousState,
		List<InitialSave> initialSaves
	) {
		return new LoadResult(
			getChanges(previousState.effectiveValues(), false, previousState.usesDeclaredDefaults()),
			getChanges(previousState.pendingValues(), true, previousState.usesDeclaredDefaults()),
			List.copyOf(initialSaves)
		);
	}

	private @Nullable String load(@Nullable Path path, Map<Path, ConfigFileReader.Contents> preparedFileLoads) {
		if (path == null || !Files.exists(path)) {
			return null;
		}
		try {
			ConfigFileReader.Contents contents = preparedFileLoads.get(path);
			if (contents == null) {
				return ConfigSerializer.loadWithoutNotifyingAndGetFingerprint(path, definition.categories, definition.mode.serializationSettings());
			}
			ConfigSerializer.applyContentsWithoutNotifying(path, definition.categories, definition.mode.serializationSettings(), contents);
			return contents.fingerprint();
		} catch (IOException e) {
			handleFileError("load", path, e);
			return null;
		}
	}

	private static List<InitialSave> getInitialSaves(
		@Nullable Path defaultPath,
		@Nullable Path path,
		boolean activePathChanged,
		boolean createActiveFileOnActivation,
		boolean allowActiveInitialSave,
		@Nullable String loadedFingerprint
	) {
		List<InitialSave> initialSaves = new ArrayList<>(2);
		if (defaultPath != null && !Files.exists(defaultPath)) {
			initialSaves.add(new InitialSave(defaultPath, true, null));
		}
		if (allowActiveInitialSave && path != null && !path.equals(defaultPath) && activePathChanged &&
			(createActiveFileOnActivation || defaultPath == null || Files.exists(path))
		) {
			initialSaves.add(new InitialSave(path, false, loadedFingerprint));
		}
		return List.copyOf(initialSaves);
	}

	private Map<ConfigValue<?>, Object> getEffectiveValues() {
		Map<ConfigValue<?>, Object> values = new IdentityHashMap<>();
		getConfigValues().forEach(configValue -> values.put(configValue, configValue.getEffectiveValueWithoutLoading()));
		return values;
	}

	private Map<ConfigValue<?>, Object> getPendingValues() {
		Map<ConfigValue<?>, Object> values = new IdentityHashMap<>();
		getConfigValues().forEach(configValue -> values.put(configValue, configValue.getPendingValueWithoutLoading()));
		return values;
	}

	private List<AppliedConfigValueChange<?>> getEffectiveChanges(Map<ConfigValue<?>, Object> previousValues) {
		return getChanges(previousValues, false);
	}

	private List<AppliedConfigValueChange<?>> getPendingChanges(Map<ConfigValue<?>, Object> previousValues) {
		return getChanges(previousValues, true);
	}

	private List<AppliedConfigValueChange<?>> getChanges(
		Map<ConfigValue<?>, Object> previousValues,
		boolean pending
	) {
		return getChanges(previousValues, pending, false);
	}

	private List<AppliedConfigValueChange<?>> getChanges(
		Map<ConfigValue<?>, Object> previousValues,
		boolean pending,
		boolean previouslyUsedDeclaredDefaults
	) {
		List<AppliedConfigValueChange<?>> changes = new ArrayList<>();
		for (ConfigValue<?> configValue : getConfigValues()) {
			Object oldValue = previousValues.get(configValue);
			if (previouslyUsedDeclaredDefaults) {
				oldValue = configValue.getDefaultValue();
			}
			AppliedConfigValueChange<?> change = getChange(configValue, oldValue, pending, usesDeclaredDefaults());
			if (change != null) {
				changes.add(change);
			}
		}
		return List.copyOf(changes);
	}

	private Collection<ConfigValue<?>> getConfigValues() {
		return definition.categories.stream()
			.flatMap(category -> category.getConfigValues().stream())
			.toList();
	}

	@SuppressWarnings("unchecked")
	private static <T> @Nullable AppliedConfigValueChange<T> getChange(
		ConfigValue<T> configValue,
		Object oldValue,
		boolean pending,
		boolean useDeclaredDefault
	) {
		T currentValue;
		if (useDeclaredDefault) {
			currentValue = configValue.getDefaultValue();
		} else if (pending) {
			currentValue = configValue.getPendingValueWithoutLoading();
		} else {
			currentValue = configValue.getEffectiveValueWithoutLoading();
		}
		if (!Objects.equals(oldValue, currentValue)) {
			return new AppliedConfigValueChange<>(configValue, (T) oldValue, currentValue);
		}
		return null;
	}

	private void resetValuesToDefaults() {
		getConfigValues().forEach(ConfigValue::resetToDefaultWithoutNotifying);
	}

	private void resetAllValuesToDefaults() {
		getConfigValues().forEach(ConfigValue::resetAllToDefaultWithoutNotifying);
	}

	private void restoreValues(
		Map<ConfigValue<?>, Object> effectiveValues,
		Map<ConfigValue<?>, Object> pendingValues
	) {
		for (ConfigValue<?> configValue : getConfigValues()) {
			restoreValue(configValue, effectiveValues.get(configValue), pendingValues.get(configValue));
		}
	}

	private static <T> void restoreValue(ConfigValue<T> configValue, Object effectiveValue, Object pendingValue) {
		@SuppressWarnings("unchecked")
		T typedEffectiveValue = (T) effectiveValue;
		@SuppressWarnings("unchecked")
		T typedPendingValue = (T) pendingValue;
		configValue.setSynchronizedValuesWithoutNotifying(typedEffectiveValue, typedPendingValue);
	}

	public synchronized void promotePendingValuesAfterWorldRestart() {
		loadIfNeeded();
		if (activeFiles.path() == null) {
			return;
		}
		ConfigValueRestartRequirement boundary = ConfigValueRestartRequirement.WORLD_RESTART;
		if (!restartValuesInitialized) {
			boundary = ConfigValueRestartRequirement.GAME_RESTART;
		}
		List<AppliedConfigValueChange<?>> changes = promotePendingValuesWithoutNotifying(boundary);
		restartValuesInitialized = true;
		notifyChanges(changes, List.of());
	}

	private List<AppliedConfigValueChange<?>> promotePendingValuesWithoutNotifying(
		ConfigValueRestartRequirement boundary
	) {
		List<AppliedConfigValueChange<?>> changes = new ArrayList<>();
		for (ConfigValue<?> configValue : getConfigValues()) {
			ConfigValueRestartRequirement requirement = configValue.getRestartRequirement();
			if (requirement == ConfigValueRestartRequirement.NONE) {
				continue;
			}
			if (boundary == ConfigValueRestartRequirement.WORLD_RESTART && requirement != boundary) {
				continue;
			}
			AppliedConfigValueChange<?> change = configValue.promotePendingValueWithoutNotifying();
			if (change != null) {
				changes.add(change);
			}
		}
		return List.copyOf(changes);
	}

	private void setActivePaths(@Nullable Path defaultPath, @Nullable Path path) {
		if (Objects.equals(activeFiles.defaultPath(), defaultPath) && Objects.equals(activeFiles.path(), path)) {
			return;
		}
		flushPendingSaveIfNeeded();
		ActiveFiles nextFiles = watchFiles(defaultPath, path);
		try {
			activeFiles.close();
		} catch (RuntimeException | Error e) {
			nextFiles.closeAfterFailure(e);
			throw e;
		}
		pendingFileChanges.clear();
		activeFiles = nextFiles;
	}

	private ActiveFiles watchFiles(@Nullable Path defaultPath, @Nullable Path path) {
		Runnable removeDefaultCallback = null;
		if (fileWatcher != null && defaultPath != null) {
			removeDefaultCallback = fileWatcher.addCallback(defaultPath, () -> onFileChanged(defaultPath));
		}
		try {
			Runnable removeCallback = null;
			if (fileWatcher != null && path != null && !path.equals(defaultPath)) {
				removeCallback = fileWatcher.addCallback(path, () -> onFileChanged(path));
			}
			return new ActiveFiles(defaultPath, path, removeDefaultCallback, removeCallback);
		} catch (RuntimeException | Error e) {
			if (removeDefaultCallback != null) {
				try {
					removeDefaultCallback.run();
				} catch (RuntimeException | Error cleanupFailure) {
					e.addSuppressed(cleanupFailure);
				}
			}
			throw e;
		}
	}

	private void updatePathReservations(@Nullable Path defaultPath, @Nullable Path path) {
		updatePathReservations(defaultPath, path, null, null);
	}

	private void updatePathReservations(
		@Nullable Path defaultPath,
		@Nullable Path path,
		@Nullable Path previousDefaultPath,
		@Nullable Path previousPath
	) {
		List<Path> paths = new ArrayList<>(pathResolver.getPersistentReservationPaths());
		addPathIfPresent(paths, defaultPath);
		addPathIfPresent(paths, path);
		addPathIfPresent(paths, previousDefaultPath);
		addPathIfPresent(paths, previousPath);
		pathReservation.accept(paths);
	}

	private static void addPathIfPresent(List<Path> paths, @Nullable Path path) {
		if (path != null) {
			paths.add(path);
		}
	}

	private void transitionActivePaths(
		@Nullable Path defaultPath,
		@Nullable Path path,
		@Nullable Path previousDefaultPath,
		@Nullable Path previousPath
	) {
		// Another config must not use the old files until we finish saving changes to them.
		updatePathReservations(defaultPath, path, previousDefaultPath, previousPath);
		try {
			setActivePaths(defaultPath, path);
		} catch (RuntimeException | Error e) {
			try {
				updatePathReservations(previousDefaultPath, previousPath);
			} catch (RuntimeException | Error reservationFailure) {
				e.addSuppressed(reservationFailure);
			}
			throw e;
		}
		updatePathReservations(defaultPath, path);
	}

	private void flushPendingSaveIfNeeded() {
		Path pendingPath = pendingSavePath;
		if (pendingPath != null && Objects.equals(activeFiles.path(), pendingPath)) {
			save(pendingPath);
		}
	}

	private synchronized void onFileChanged(Path changedPath) {
		if (!Objects.equals(changedPath, activeFiles.defaultPath()) && !Objects.equals(changedPath, activeFiles.path())) {
			return;
		}
		pendingFileChanges.add(changedPath);
		pendingLoad.add(PendingLoad.RELOAD_VALUES);
		if (serverSynchronization != null) {
			serverSynchronization.notifyChanged();
		}
	}

	private void closeFailedInitialization(Throwable failure) {
		ActiveFiles files = activeFiles;
		activeFiles = ActiveFiles.NONE;
		files.closeAfterFailure(failure);
		try {
			pathReservation.accept(List.of());
		} catch (RuntimeException | Error rollbackFailure) {
			failure.addSuppressed(rollbackFailure);
		}
		pendingSavePath = null;
	}

	private synchronized void saveAfterLocalizationLoads(Path path, int attempt) {
		if (!Objects.equals(path, activeFiles.path()) && !Objects.equals(path, pendingSavePath)) {
			return;
		}
		if (!definition.mode.waitForLocalization() || ConfigSerializer.canLocalizeComments()) {
			save(path);
			return;
		}
		if (attempt == 0) {
			LOGGER.debug("Localization has not loaded yet, waiting to save the config file: {}", path);
		}
		if (attempt >= LOCALIZATION_SAVE_RETRY_LIMIT) {
			LOGGER.debug("Localization did not load before the config save retry limit, saving with translation keys: {}", path);
			save(path);
			return;
		}
		delayedSave.run(() -> saveAfterLocalizationLoads(path, attempt + 1));
	}

	private synchronized void saveInitialFileAfterLocalizationLoads(InitialSave initialSave, int attempt) {
		Path path = initialSave.path();
		if (initialSave.defaults()) {
			if (!Objects.equals(path, activeFiles.defaultPath()) || Files.exists(path)) {
				return;
			}
		} else if (!Objects.equals(path, activeFiles.path())) {
			return;
		}
		if (!definition.mode.waitForLocalization() || ConfigSerializer.canLocalizeComments()) {
			saveInitialFile(initialSave);
			return;
		}
		if (attempt == 0) {
			LOGGER.debug("Localization has not loaded yet, waiting to save the config file: {}", path);
		}
		if (attempt >= LOCALIZATION_SAVE_RETRY_LIMIT) {
			LOGGER.debug("Localization did not load before the config save retry limit, saving with translation keys: {}", path);
			saveInitialFile(initialSave);
			return;
		}
		delayedSave.run(() -> saveInitialFileAfterLocalizationLoads(initialSave, attempt + 1));
	}

	private synchronized void saveInitialFile(InitialSave initialSave) {
		try {
			if (initialSave.defaults()) {
				saveDefaultIfMissing(initialSave.path());
			} else {
				write(initialSave.path(), initialSave.loadedFingerprint());
			}
		} catch (IOException e) {
			handleFileError("save", initialSave.path(), e);
		}
	}

	private synchronized void save(Path path) {
		try {
			write(path);
		} catch (IOException e) {
			handleFileError("save", path, e);
		} finally {
			if (Objects.equals(pendingSavePath, path)) {
				pendingSavePath = null;
			}
		}
	}

	private void write(Path path) throws IOException {
		write(path, null);
	}

	private void write(Path path, @Nullable String loadedFingerprint) throws IOException {
		Path defaultPath = activeFiles.defaultPath();
		if (defaultPath != null) {
			saveDefaultIfMissing(defaultPath);
		}
		if (definition.mode.serializationSettings().localizeComments()) {
			logUntranslatedKeysIfNeeded(path);
		}
		try {
			ConfigSerializer.save(path, definition.categories, definition.mode.serializationSettings(), loadedFingerprint);
		} catch (IOException e) {
			if (definition.type != ConfigSchemaType.CLIENT || loadedFingerprint == null) {
				throw e;
			}
			LOGGER.warn(
				"Failed to refresh loaded client config file '{}'; continuing with the loaded settings. " +
					"The refreshed config could not be saved. Check file permissions, read-only attributes, and other programs accessing the file.",
				path, e
			);
		}
	}

	private void handleFileError(String action, Path path, IOException error) {
		if (definition.mode.synchronousFileAccess()) {
			throw new UncheckedIOException("Failed to %s config schema: %s".formatted(action, path), error);
		}
		LOGGER.error("Failed to {} config file: '{}'", action, path, error);
	}

	private void saveDefaultIfMissing(Path path) throws IOException {
		if (!Files.exists(path)) {
			ConfigSerializer.saveDefaults(path, definition.categories, definition.mode.serializationSettings());
		}
	}

	private void logUntranslatedKeysIfNeeded(Path path) {
		if (!logUntranslatedKeys || translationKeysChecked || !ConfigSerializer.canLocalizeComments()) {
			return;
		}
		translationKeysChecked = true;
		ConfigTranslationChecker.logUntranslatedKeys(path, definition.editorCategories, definition.categories);
	}

	public synchronized void logUntranslatedKeysIfReady() {
		Path path = activeFiles.path();
		if (path == null) {
			path = activeFiles.defaultPath();
		}
		if (path != null) {
			logUntranslatedKeysIfNeeded(path);
		}
	}

	public synchronized void markDirty() {
		Path path = activeFiles.path();
		if (path == null) {
			return;
		}
		pendingSavePath = path;
		delayedSave.run(() -> saveAfterLocalizationLoads(path, 0));
	}

	@Override
	public <T> boolean setValue(ConfigValue<T> value, T newValue) {
		return !batchUpdate(updater -> updater.set(value, newValue)).isEmpty();
	}

	@Override
	public synchronized List<? extends IAppliedConfigValueChange<?>> batchUpdate(Consumer<IConfigBatchUpdater> updateBatch) {
		ConfigBatchUpdater updater = createBatchUpdater(updateBatch);
		return applyBatchUpdates(updater.getUpdates());
	}

	private static ConfigBatchUpdater createBatchUpdater(Consumer<IConfigBatchUpdater> updateBatch) {
		ErrorUtil.checkNotNull(updateBatch, "updateBatch");
		ConfigBatchUpdater updater = new ConfigBatchUpdater();
		try {
			updateBatch.accept(updater);
		} finally {
			updater.close();
		}
		return updater;
	}

	synchronized List<AppliedConfigValueChange<?>> applyBatchUpdates(List<? extends ConfigValueUpdate<?>> updates) {
		ErrorUtil.checkNotNull(updates, "updates");
		if (updates.isEmpty()) {
			return List.of();
		}

		validateUpdates(updates);
		loadIfNeeded();
		if (activeFiles.path() == null) {
			throw new IllegalStateException("Config schema has no active backing file.");
		}
		Map<ConfigValue<?>, Object> updatedValues = getUpdatedValues(updates);
		ConfigSerializer.validatePendingSave(definition.categories, definition.mode.serializationSettings(), updatedValues);
		validateProspectiveServerSnapshot(updatedValues);

		Map<ConfigValue<?>, Object> previousEffectiveValues = getEffectiveValues();
		List<AppliedConfigValueChange<?>> pendingChanges = applyUpdatesAtomically(updates);
		if (pendingChanges.isEmpty()) {
			return List.of();
		}

		markDirty();
		List<AppliedConfigValueChange<?>> effectiveChanges = getEffectiveChanges(previousEffectiveValues);
		notifyChanges(effectiveChanges, pendingChanges);
		return List.copyOf(pendingChanges);
	}

	private static List<AppliedConfigValueChange<?>> applyUpdatesAtomically(
		List<? extends ConfigValueUpdate<?>> updates
	) {
		List<ConfigValueUpdate<?>> rollbacks = new ArrayList<>();
		for (ConfigValueUpdate<?> update : updates) {
			rollbacks.add(createRollbackUpdate(update));
		}
		List<AppliedConfigValueChange<?>> changes = new ArrayList<>();
		try {
			for (ConfigValueUpdate<?> update : updates) {
				AppliedConfigValueChange<?> change = update.apply();
				if (change != null) {
					changes.add(change);
				}
			}
			return List.copyOf(changes);
		} catch (RuntimeException | Error e) {
			for (int i = rollbacks.size() - 1; i >= 0; i--) {
				try {
					rollbacks.get(i).apply();
				} catch (RuntimeException | Error rollbackFailure) {
					e.addSuppressed(rollbackFailure);
				}
			}
			throw e;
		}
	}

	private static <T> ConfigValueUpdate<T> createRollbackUpdate(ConfigValueUpdate<T> update) {
		ConfigValue<T> configValue = update.configValue();
		return new ConfigValueUpdate<>(configValue, configValue.getPendingValueWithoutLoading());
	}

	private void validateUpdates(List<? extends ConfigValueUpdate<?>> updates) {
		Set<ConfigValue<?>> updatedValues = new HashSet<>();
		for (ConfigValueUpdate<?> update : updates) {
			ConfigValue<?> configValue = update.configValue();
			if (!containsConfigValue(configValue)) {
				throw new IllegalArgumentException("Config value does not belong to this schema: " + configValue.getName());
			}
			if (!updatedValues.add(configValue)) {
				throw new IllegalArgumentException("Config value cannot be updated more than once in one batch: " + configValue.getName());
			}
		}
	}

	boolean containsConfigValue(ConfigValue<?> configValue) {
		return definition.categories.stream()
			.flatMap(category -> category.getConfigValues().stream())
			.anyMatch(value -> value == configValue);
	}

	private static Map<ConfigValue<?>, Object> getUpdatedValues(List<? extends ConfigValueUpdate<?>> updates) {
		Map<ConfigValue<?>, Object> updatedValues = new IdentityHashMap<>();
		updates.forEach(update -> updatedValues.put(update.configValue(), update.newValue()));
		return updatedValues;
	}

	@Override
	public Runnable addBatchListener(IConfigValueBatchChangeListener listener) {
		return definition.addBatchListener(listener);
	}

	@Override
	public Runnable addPendingBatchListener(IConfigValueBatchChangeListener listener) {
		return definition.addPendingBatchListener(listener);
	}

	private void notifyChanges(
		List<? extends AppliedConfigValueChange<?>> effectiveChanges,
		List<? extends AppliedConfigValueChange<?>> pendingChanges
	) {
		if (effectiveChanges.isEmpty() && pendingChanges.isEmpty()) {
			return;
		}
		changeVersion.incrementAndGet();
		List<AppliedConfigValueChange<?>> immutablePendingChanges = List.copyOf(pendingChanges);
		List<AppliedConfigValueChange<?>> immutableEffectiveChanges = List.copyOf(effectiveChanges);
		Runnable pendingNotifications = ConfigValue.snapshotChangedValueNotifications(immutablePendingChanges, true);
		Runnable effectiveNotifications = ConfigValue.snapshotChangedValueNotifications(immutableEffectiveChanges, false);
		List<IConfigValueBatchChangeListener> pendingSchemaListeners = definition.pendingBatchListeners.snapshot();
		List<IConfigValueBatchChangeListener> effectiveSchemaListeners = definition.batchListeners.snapshot();
		if (!immutablePendingChanges.isEmpty()) {
			pendingNotifications.run();
			notifyListeners(immutablePendingChanges, pendingSchemaListeners, "pending config schema");
		}
		if (!immutableEffectiveChanges.isEmpty()) {
			effectiveNotifications.run();
			notifyListeners(immutableEffectiveChanges, effectiveSchemaListeners, "config schema");
			if (serverSynchronization != null) {
				serverSynchronization.notifyChanged();
			}
		}
	}

	private void notifyListeners(
		List<? extends IAppliedConfigValueChange<?>> changes,
		List<IConfigValueBatchChangeListener> registeredListeners,
		String description
	) {
		for (IConfigValueBatchChangeListener listener : registeredListeners) {
			try {
				listener.onConfigValuesChanged(changes);
			} catch (RuntimeException e) {
				LOGGER.error("{} listener failed for '{}'.", description, activeFiles.path(), e);
			}
		}
	}

	@Override
	public List<ConfigCategory> getCategories() {
		return definition.categories;
	}

	@Override
	public List<ConfigEditorCategory> getEditorCategories() {
		return definition.editorCategories;
	}

	@Override
	public String getId() {
		return definition.id;
	}

	@Override
	public String getModId() {
		return definition.modId;
	}

	@Override
	public ConfigSchemaType getType() {
		return definition.type;
	}

	private boolean isSynchronizedServerSchema() {
		return definition.type == ConfigSchemaType.SERVER;
	}

	@Override
	public synchronized boolean isActive() {
		loadIfNeeded();
		return activeFiles.path() != null || (isSynchronizedServerSchema() && remotelyActive);
	}

	@Override
	public synchronized Optional<Path> getPath() {
		loadIfNeeded();
		return Optional.ofNullable(activeFiles.path());
	}

	public long getChangeVersion() {
		return changeVersion.get();
	}

	public synchronized <T> T getEffectiveValue(ConfigValue<T> configValue) {
		loadIfNeeded();
		if (usesDeclaredDefaults()) {
			return configValue.getDefaultValue();
		}
		return configValue.getEffectiveValueWithoutLoading();
	}

	public synchronized <T> T getPendingValue(ConfigValue<T> configValue) {
		loadIfNeeded();
		if (usesDeclaredDefaults()) {
			return configValue.getDefaultValue();
		}
		return configValue.getPendingValueWithoutLoading();
	}

	private boolean usesDeclaredDefaults() {
		return definition.type != ConfigSchemaType.CLIENT && activeFiles.path() == null && !remotelyActive;
	}

	public synchronized List<ServerConfigValueData> serializeValues() {
		loadIfNeeded();
		return serializeCurrentValues();
	}

	private List<ServerConfigValueData> serializeCurrentValues() {
		List<ServerConfigValueData> values = new ArrayList<>();
		for (ConfigCategory category : definition.categories) {
			for (ConfigValue<?> value : category.getConfigValues()) {
				values.add(serializeValue(category.getName(), value));
			}
		}
		return List.copyOf(values);
	}

	private void validateCurrentServerSnapshot() {
		validateServerSnapshots(Map.of());
	}

	private void validateProspectiveServerSnapshot(Map<ConfigValue<?>, Object> updatedValues) {
		if (!isSynchronizedServerSchema()) {
			return;
		}
		validateServerSnapshots(updatedValues);
	}

	private void validateServerSnapshots(Map<ConfigValue<?>, Object> updatedValues) {
		ServerConfigRuntime.validateSnapshot(
			definition.getServerKey(),
			serializeProspectiveValues(updatedValues, ServerSnapshotState.CURRENT)
		);
		boolean hasWorldRestartValue = getConfigValues().stream()
			.anyMatch(value -> value.getRestartRequirement() == ConfigValueRestartRequirement.WORLD_RESTART);
		boolean hasGameRestartValue = getConfigValues().stream()
			.anyMatch(value -> value.getRestartRequirement() == ConfigValueRestartRequirement.GAME_RESTART);
		if (hasWorldRestartValue) {
			ServerConfigRuntime.validateSnapshot(
				definition.getServerKey(),
				serializeProspectiveValues(updatedValues, ServerSnapshotState.AFTER_WORLD_RESTART)
			);
		}
		if (hasGameRestartValue) {
			ServerConfigRuntime.validateSnapshot(
				definition.getServerKey(),
				serializeProspectiveValues(updatedValues, ServerSnapshotState.AFTER_GAME_RESTART)
			);
		}
	}

	private List<ServerConfigValueData> serializeProspectiveValues(
		Map<ConfigValue<?>, Object> updatedValues,
		ServerSnapshotState state
	) {
		List<ServerConfigValueData> values = new ArrayList<>();
		for (ConfigCategory category : definition.categories) {
			for (ConfigValue<?> value : category.getConfigValues()) {
				values.add(serializeProspectiveValue(category.getName(), value, updatedValues, state));
			}
		}
		return List.copyOf(values);
	}

	private static <T> ServerConfigValueData serializeProspectiveValue(
		String categoryName,
		ConfigValue<T> value,
		Map<ConfigValue<?>, Object> updatedValues,
		ServerSnapshotState state
	) {
		Object rawPendingValue = updatedValues.getOrDefault(value, value.getPendingValueWithoutLoading());
		@SuppressWarnings("unchecked")
		T pendingValue = (T) rawPendingValue;
		T effectiveValue = value.getEffectiveValueWithoutLoading();
		ConfigValueRestartRequirement restartRequirement = value.getRestartRequirement();
		boolean updatedImmediately = updatedValues.containsKey(value) && restartRequirement == ConfigValueRestartRequirement.NONE;
		boolean promotedAfterWorldRestart = state != ServerSnapshotState.CURRENT &&
			restartRequirement == ConfigValueRestartRequirement.WORLD_RESTART;
		boolean promotedAfterGameRestart = state == ServerSnapshotState.AFTER_GAME_RESTART &&
			restartRequirement == ConfigValueRestartRequirement.GAME_RESTART;
		if (updatedImmediately || promotedAfterWorldRestart || promotedAfterGameRestart) {
			effectiveValue = pendingValue;
		}
		return new ServerConfigValueData(
			categoryName,
			value.getName(),
			ConfigFileValueAdapter.serialize(value.getSerializer(), effectiveValue)
		);
	}

	private static <T> ServerConfigValueData serializeValue(String categoryName, ConfigValue<T> value) {
		return new ServerConfigValueData(
			categoryName,
			value.getName(),
			ConfigFileValueAdapter.serialize(value.getSerializer(), value.getEffectiveValueWithoutLoading())
		);
	}

	private List<ResolvedServerConfigValue> resolveServerValues(
		List<ServerConfigValueData> values,
		boolean allowSchemaDifferences
	) {
		ErrorUtil.checkNotNull(values, "values");
		Set<ConfigValue<?>> resolvedValues = new HashSet<>();
		List<ResolvedServerConfigValue> results = new ArrayList<>();
		for (ServerConfigValueData data : values) {
			Optional<ConfigCategory> optionalCategory = definition.categories.stream()
				.filter(candidate -> candidate.getName().equals(data.categoryName()))
				.findFirst();
			if (optionalCategory.isEmpty()) {
				if (allowSchemaDifferences) {
					continue;
				}
				throw new IllegalArgumentException("Unknown config category: " + data.categoryName());
			}
			Optional<ConfigValue<?>> optionalConfigValue = optionalCategory.orElseThrow()
				.getConfigValue(data.valueName());
			if (optionalConfigValue.isEmpty()) {
				if (allowSchemaDifferences) {
					continue;
				}
				throw new IllegalArgumentException("Unknown config value: " + data.categoryName() + "." + data.valueName());
			}
			ConfigValue<?> configValue = optionalConfigValue.orElseThrow();
			if (!resolvedValues.add(configValue)) {
				throw new IllegalArgumentException("Config value was provided more than once: " + data.categoryName() + "." + data.valueName());
			}
			results.add(new ResolvedServerConfigValue(configValue, data));
		}
		return List.copyOf(results);
	}

	private static <T> T deserializeValue(ConfigValue<T> configValue, String serializedValue) {
		IDeserializeResult<JsonElement> decodedValue = ConfigFileValueCodec.deserialize(serializedValue);
		if (!decodedValue.getDiagnostics().isEmpty() || decodedValue.getResult().isEmpty()) {
			String diagnostics = String.join("; ", decodedValue.getDiagnostics());
			throw new IllegalArgumentException("Invalid value for '%s': %s".formatted(configValue.getName(), diagnostics));
		}
		IDeserializeResult<T> result = ConfigFileValueAdapter.deserialize(
			configValue.getSerializer(),
			decodedValue.getResult().orElseThrow()
		);
		if (!result.getDiagnostics().isEmpty() || result.getResult().isEmpty()) {
			String diagnostics = String.join("; ", result.getDiagnostics());
			throw new IllegalArgumentException("Invalid value for '%s': %s".formatted(configValue.getName(), diagnostics));
		}
		return result.getResult().orElseThrow();
	}

	private synchronized void applyRemoteSnapshot(List<ServerConfigValueData> values) {
		List<SynchronizedConfigValue<?>> synchronizedValues = new ArrayList<>();
		for (ResolvedServerConfigValue value : resolveServerValues(values, true)) {
			synchronizedValues.add(deserializeSynchronizedValue(value));
		}
		loadIfNeeded();
		if (activeFiles.path() != null) {
			return;
		}
		Map<ConfigValue<?>, Object> previousEffectiveValues = getEffectiveValues();
		Map<ConfigValue<?>, Object> previousPendingValues = getPendingValues();
		boolean previouslyUsedDeclaredDefaults = usesDeclaredDefaults();
		resetAllValuesToDefaults();
		synchronizedValues.forEach(SynchronizedConfigValue::apply);
		remotelyActive = true;
		pendingLoad.remove(PendingLoad.RELOAD_VALUES);
		List<AppliedConfigValueChange<?>> effectiveChanges = getChanges(previousEffectiveValues, false, previouslyUsedDeclaredDefaults);
		List<AppliedConfigValueChange<?>> pendingChanges = getChanges(previousPendingValues, true, previouslyUsedDeclaredDefaults);
		notifyChanges(effectiveChanges, pendingChanges);
	}

	private static <T> SynchronizedConfigValue<T> deserializeSynchronizedValue(ResolvedServerConfigValue value) {
		@SuppressWarnings("unchecked")
		ConfigValue<T> configValue = (ConfigValue<T>) value.configValue();
		T synchronizedValue = deserializeValue(configValue, value.data().serializedValue());
		return new SynchronizedConfigValue<>(configValue, synchronizedValue);
	}

	private synchronized void clearRemoteSnapshot() {
		if (!remotelyActive) {
			return;
		}
		Map<ConfigValue<?>, Object> previousEffectiveValues = getEffectiveValues();
		Map<ConfigValue<?>, Object> previousPendingValues = getPendingValues();
		remotelyActive = false;
		resetAllValuesToDefaults();
		pendingLoad.add(PendingLoad.RELOAD_VALUES);
		List<AppliedConfigValueChange<?>> effectiveChanges = getEffectiveChanges(previousEffectiveValues);
		List<AppliedConfigValueChange<?>> pendingChanges = getPendingChanges(previousPendingValues);
		notifyChanges(effectiveChanges, pendingChanges);
	}

	public Optional<ServerSynchronization> getServerSynchronization() {
		return Optional.ofNullable(serverSynchronization);
	}

	/** Server synchronization operations for an initialized server schema. */
	public final class ServerSynchronization {
		private final ServerConfigKey key;
		private final ListenerList<Runnable> changeListeners = new ListenerList<>();

		private ServerSynchronization(ServerConfigKey key) {
			this.key = key;
		}

		public ConfigSchema getSchema() {
			return ConfigSchema.this;
		}

		public ServerConfigKey getKey() {
			return key;
		}

		public Runnable addChangeListener(Runnable listener) {
			return changeListeners.add(listener);
		}

		private void notifyChanged() {
			changeListeners.snapshot().forEach(Runnable::run);
		}

		public List<ServerConfigValueData> serializeValues() {
			return ConfigSchema.this.serializeValues();
		}

		public void applyRemoteSnapshot(List<ServerConfigValueData> values) {
			ConfigSchema.this.applyRemoteSnapshot(values);
		}

		public void clearRemoteSnapshot() {
			ConfigSchema.this.clearRemoteSnapshot();
		}
	}

	private record ActiveFiles(
		@Nullable Path defaultPath,
		@Nullable Path path,
		@Nullable Runnable removeDefaultCallback,
		@Nullable Runnable removeCallback
	) {
		private static final ActiveFiles NONE = new ActiveFiles(null, null, null, null);

		private void close() {
			if (removeDefaultCallback == null) {
				if (removeCallback != null) {
					removeCallback.run();
				}
				return;
			}
			try {
				removeDefaultCallback.run();
			} catch (RuntimeException | Error e) {
				if (removeCallback != null) {
					try {
						removeCallback.run();
					} catch (RuntimeException | Error secondFailure) {
						e.addSuppressed(secondFailure);
					}
				}
				throw e;
			}
			if (removeCallback != null) {
				removeCallback.run();
			}
		}

		private void closeAfterFailure(Throwable failure) {
			try {
				close();
			} catch (RuntimeException | Error cleanupFailure) {
				failure.addSuppressed(cleanupFailure);
			}
		}
	}

	private enum PendingLoad {
		CHECK_PATHS,
		RELOAD_VALUES
	}

	private record LoadState(
		Map<ConfigValue<?>, Object> effectiveValues,
		Map<ConfigValue<?>, Object> pendingValues,
		boolean restartValuesInitialized,
		boolean remotelyActive,
		boolean usesDeclaredDefaults
	) {}

	private record FileLoadResult(
		LoadState previousState,
		List<InitialSave> initialSaves
	) {}

	private record LoadResult(
		List<AppliedConfigValueChange<?>> effectiveChanges,
		List<AppliedConfigValueChange<?>> pendingChanges,
		List<InitialSave> initialSaves
	) {}

	private record ResolvedServerConfigValue(
		ConfigValue<?> configValue,
		ServerConfigValueData data
	) {}

	private record SynchronizedConfigValue<T>(
		ConfigValue<T> configValue,
		T value
	) {
		private void apply() {
			configValue.setSynchronizedValuesWithoutNotifying(value, value);
		}
	}

	private record InitialSave(
		Path path,
		boolean defaults,
		@Nullable String loadedFingerprint
	) {}

	private enum ServerSnapshotState {
		CURRENT,
		AFTER_WORLD_RESTART,
		AFTER_GAME_RESTART
	}

	private enum MigrationAttempt {
		NOT_ATTEMPTED,
		SKIPPED,
		MIGRATED,
		FAILED
	}
}
