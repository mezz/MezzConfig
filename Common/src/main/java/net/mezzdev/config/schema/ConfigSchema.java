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
import net.mezzdev.config.value.AppliedConfigValueChange;
import net.mezzdev.config.value.ConfigValueUpdate;
import net.mezzdev.config.sorting.SortingConfig;
import net.mezzdev.deduplicatingrunner.DeduplicatingRunner;
import net.mezzdev.deduplicatingrunner.DelayedTaskScheduler;
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
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ConfigSchema implements IConfigSchema {
	private static final Logger LOGGER = LogManager.getLogger();
	static final String DEFAULT_MOD_ID = "mezz_config";
	private static final Duration SAVE_DELAY_TIME = Duration.ofSeconds(2);
	private static final int LOCALIZATION_SAVE_RETRY_LIMIT = 30;
	private static final Consumer<? super Collection<Path>> NO_PATH_RESERVATION = ignored -> {};

	private final String id;
	private final String modId;
	private final ConfigSchemaPathResolver pathResolver;
	private final ConfigSchemaType type;
	private final ConfigSchemaMode mode;
	private final @Nullable ServerConfigKey serverKey;
	private final List<ConfigCategory> categories;
	private final List<ConfigEditorCategory> editorCategories;
	private final @Nullable ConfigMigrationSpec migrationSpec;
	private final AtomicBoolean needsLoad = new AtomicBoolean(true);
	private final AtomicLong changeVersion = new AtomicLong();
	private final DeduplicatingRunner delayedSave;
	private @Nullable FileWatcher fileWatcher;
	private @Nullable Path activeDefaultPath;
	private @Nullable Path activePath;
	private @Nullable Path pendingSavePath;
	private @Nullable Runnable removeDefaultFileWatcherCallback;
	private @Nullable Runnable removeFileWatcherCallback;
	private final ListenerList<IConfigValueBatchChangeListener> batchListeners = new ListenerList<>();
	private final ListenerList<IConfigValueBatchChangeListener> pendingBatchListeners = new ListenerList<>();
	private boolean registered;
	private boolean registrationInProgress;
	private boolean restartValuesInitialized;
	private boolean logUntranslatedKeys;
	private boolean translationKeysChecked;
	private volatile boolean remotelyActive;
	private boolean migrationCompleted;
	private Consumer<? super Collection<Path>> pathReservation = NO_PATH_RESERVATION;

	public ConfigSchema(
		Path path,
		List<ConfigCategoryBuilder> categoryBuilders,
		DelayedTaskScheduler scheduler
	) {
		this(DEFAULT_MOD_ID, path, categoryBuilders, List.copyOf(categoryBuilders), scheduler);
	}

	public ConfigSchema(
		ConfigSchemaPathResolver pathResolver,
		List<ConfigCategoryBuilder> categoryBuilders,
		DelayedTaskScheduler scheduler
	) {
		this(DEFAULT_MOD_ID, pathResolver, categoryBuilders, List.copyOf(categoryBuilders), scheduler);
	}

	public ConfigSchema(
		String modId,
		Path path,
		List<ConfigCategoryBuilder> categoryBuilders,
		DelayedTaskScheduler scheduler
	) {
		this(modId, path, categoryBuilders, List.copyOf(categoryBuilders), scheduler);
	}

	public ConfigSchema(
		String modId,
		ConfigSchemaPathResolver pathResolver,
		List<ConfigCategoryBuilder> categoryBuilders,
		DelayedTaskScheduler scheduler
	) {
		this(modId, pathResolver, categoryBuilders, List.copyOf(categoryBuilders), scheduler);
	}

	public ConfigSchema(
		Path path,
		List<ConfigCategoryBuilder> categoryBuilders,
		List<ConfigEditorCategoryBuilder> editorCategoryBuilders,
		DelayedTaskScheduler scheduler
	) {
		this(DEFAULT_MOD_ID, new StaticConfigSchemaPathResolver(path), categoryBuilders, editorCategoryBuilders, scheduler);
	}

	public ConfigSchema(
		ConfigSchemaPathResolver pathResolver,
		List<ConfigCategoryBuilder> categoryBuilders,
		List<ConfigEditorCategoryBuilder> editorCategoryBuilders,
		DelayedTaskScheduler scheduler
	) {
		this(DEFAULT_MOD_ID, pathResolver, categoryBuilders, editorCategoryBuilders, scheduler);
	}

	public ConfigSchema(
		String modId,
		Path path,
		List<ConfigCategoryBuilder> categoryBuilders,
		List<ConfigEditorCategoryBuilder> editorCategoryBuilders,
		DelayedTaskScheduler scheduler
	) {
		this(modId, new StaticConfigSchemaPathResolver(path), categoryBuilders, editorCategoryBuilders, scheduler);
	}

	public ConfigSchema(
		String modId,
		ConfigSchemaPathResolver pathResolver,
		List<ConfigCategoryBuilder> categoryBuilders,
		List<ConfigEditorCategoryBuilder> editorCategoryBuilders,
		DelayedTaskScheduler scheduler
	) {
		this(
			modId,
			pathResolver,
			categoryBuilders,
			editorCategoryBuilders,
			scheduler,
			ConfigSchemaType.CLIENT,
			null
		);
	}

	public ConfigSchema(
		String modId,
		ConfigSchemaPathResolver pathResolver,
		List<ConfigCategoryBuilder> categoryBuilders,
		List<ConfigEditorCategoryBuilder> editorCategoryBuilders,
		DelayedTaskScheduler scheduler,
		ConfigSchemaType type,
		@Nullable ServerConfigKey serverKey
	) {
		this(
			getDefaultId(modId, pathResolver, serverKey),
			modId,
			pathResolver,
			categoryBuilders,
			editorCategoryBuilders,
			scheduler,
			type,
			serverKey
		);
	}

	public ConfigSchema(
		String id,
		String modId,
		ConfigSchemaPathResolver pathResolver,
		List<ConfigCategoryBuilder> categoryBuilders,
		List<ConfigEditorCategoryBuilder> editorCategoryBuilders,
		DelayedTaskScheduler scheduler,
		ConfigSchemaType type,
		@Nullable ServerConfigKey serverKey
	) {
		this(
			id,
			modId,
			pathResolver,
			categoryBuilders,
			editorCategoryBuilders,
			scheduler,
			type,
			serverKey,
			null
		);
	}

	public ConfigSchema(
		String id,
		String modId,
		ConfigSchemaPathResolver pathResolver,
		List<ConfigCategoryBuilder> categoryBuilders,
		List<ConfigEditorCategoryBuilder> editorCategoryBuilders,
		DelayedTaskScheduler scheduler,
		ConfigSchemaType type,
		@Nullable ServerConfigKey serverKey,
		@Nullable ConfigMigrationSpec migrationSpec
	) {
		this.id = validateId(id);
		this.modId = validateModId(modId);
		this.pathResolver = ErrorUtil.checkNotNull(pathResolver, "pathResolver");
		this.type = ErrorUtil.checkNotNull(type, "type");
		this.mode = ConfigSchemaMode.forSchema(type);
		this.serverKey = serverKey;
		this.migrationSpec = migrationSpec;
		validateServerKey(type, serverKey);
		if (categoryBuilders.isEmpty()) {
			throw new IllegalStateException("Config schema must have at least one storage category.");
		}
		Map<ConfigCategoryBuilder, ConfigCategory> categoryMap = new IdentityHashMap<>();
		Map<ConfigEditorCategoryBuilder, ConfigEditorCategory> editorCategoryMap = new IdentityHashMap<>();
		List<ConfigCategory> categories = new ArrayList<>();
		for (ConfigCategoryBuilder categoryBuilder : categoryBuilders) {
			ConfigCategory category = categoryBuilder.build(this);
			categoryMap.put(categoryBuilder, category);
			editorCategoryMap.put(categoryBuilder, category);
			categories.add(category);
		}
		List<ConfigEditorCategory> editorCategories = new ArrayList<>();
		for (ConfigEditorCategoryBuilder editorCategoryBuilder : editorCategoryBuilders) {
			ConfigEditorCategory category = editorCategoryMap.get(editorCategoryBuilder);
			if (category == null) {
				category = editorCategoryBuilder.build();
				editorCategoryMap.put(editorCategoryBuilder, category);
			}
			editorCategories.add(category);
		}
		categoryBuilders.forEach(categoryBuilder -> categoryBuilder.resolveEditorCategories(editorCategoryBuilders, editorCategoryMap));
		this.categories = List.copyOf(categories);
		this.editorCategories = List.copyOf(editorCategories);
		ConfigSerializer.validatePendingSave(this.categories, mode.serializationSettings(), Map.of());
		this.delayedSave = new DeduplicatingRunner(SAVE_DELAY_TIME, scheduler);
	}

	private static String getDefaultId(
		String modId,
		ConfigSchemaPathResolver pathResolver,
		@Nullable ServerConfigKey serverKey
	) {
		if (serverKey != null) {
			return serverKey.configFileName();
		}
		return pathResolver.resolvePath()
			.map(path -> path.toAbsolutePath().normalize().toString())
			.orElse(modId);
	}

	private static String validateId(String id) {
		id = ErrorUtil.checkNotNull(id, "id");
		if (id.isBlank()) {
			throw new IllegalArgumentException("id must not be blank.");
		}
		return id;
	}

	public static String validateModId(String modId) {
		modId = ErrorUtil.checkNotNull(modId, "modId");
		if (modId.isBlank()) {
			throw new IllegalArgumentException("modId must not be blank.");
		}
		return modId;
	}

	static void validateServerKey(ConfigSchemaType type, @Nullable ServerConfigKey serverKey) {
		if ((type == ConfigSchemaType.SERVER) != (serverKey != null)) {
			throw new IllegalArgumentException("Server config schemas must have exactly one server key.");
		}
	}

	public synchronized void loadIfNeeded() {
		LoadResult loadResult = loadIfNeededWithoutNotifying();
		notifyChanges(loadResult.effectiveChanges(), loadResult.pendingChanges());
		if (registered) {
			for (InitialSave initialSave : loadResult.initialSaves()) {
				if (mode.synchronousFileAccess()) {
					saveInitialFile(initialSave);
				} else {
					saveInitialFileAfterLocalizationLoads(initialSave, 0);
				}
			}
		}
	}

	private synchronized LoadResult loadIfNeededWithoutNotifying() {
		LoadState previousState = new LoadState(
			getEffectiveValues(), getPendingValues(), restartValuesInitialized, remotelyActive, usesDeclaredDefaults()
		);
		Path previousDefaultPath = activeDefaultPath;
		Path previousPath = activePath;
		updatePathReservations(previousDefaultPath, previousPath);
		Path defaultPath = pathResolver.resolveDefaultPath()
			.map(Path::normalize)
			.orElse(null);
		Optional<Path> resolvedPath = pathResolver.resolvePath()
			.map(Path::normalize);
		Path path = resolvedPath.orElse(null);
		updatePathReservations(defaultPath, path, previousDefaultPath, previousPath);
		if (isSynchronizedServerSchema() && remotelyActive && path == null) {
			transitionActivePaths(defaultPath, null, previousDefaultPath, previousPath);
			needsLoad.set(false);
			return createLoadResult(previousState, List.of());
		}
		if (path != null) {
			remotelyActive = false;
		}
		boolean defaultPathChanged = !Objects.equals(defaultPath, previousDefaultPath);
		boolean activePathChanged = !Objects.equals(path, previousPath);
		boolean pathsChanged = defaultPathChanged || activePathChanged;
		if (pathsChanged) {
			transitionActivePaths(defaultPath, path, previousDefaultPath, previousPath);
			needsLoad.set(true);
		}

		if (resolvedPath.isEmpty()) {
			boolean shouldInitializeDefault = needsLoad.getAndSet(false);
			List<InitialSave> initialSaves = List.of();
			if (shouldInitializeDefault) {
				initialSaves = getInitialSaves(defaultPath, null, activePathChanged, false, true);
			}
			if (previousPath != null) {
				resetValuesToDefaults();
				return completeServerLoad(
					previousState,
					initialSaves
				);
			}
			if (shouldInitializeDefault) {
				return completeServerLoad(
					previousState,
					initialSaves
				);
			}
			return createLoadResult(previousState, initialSaves);
		}

		if (!needsLoad.compareAndSet(true, false)) {
			return createLoadResult(previousState, List.of());
		}

		resetValuesToDefaults();
		load(defaultPath);
		MigrationAttempt migrationAttempt = attemptMigration(path);
		if (migrationAttempt != MigrationAttempt.MIGRATED) {
			load(path);
		}
		if (!restartValuesInitialized) {
			promotePendingValuesWithoutNotifying(ConfigValueRestartRequirement.GAME_RESTART);
			restartValuesInitialized = true;
		}
		return completeServerLoad(
			previousState,
			getInitialSaves(
				defaultPath,
				path,
				activePathChanged,
				isSynchronizedServerSchema(),
				migrationAttempt != MigrationAttempt.FAILED
			)
		);
	}

	private MigrationAttempt attemptMigration(Path destinationPath) {
		ConfigMigrationSpec migrationSpec = this.migrationSpec;
		if (migrationSpec == null || migrationCompleted) {
			return MigrationAttempt.NOT_ATTEMPTED;
		}
		destinationPath = destinationPath.toAbsolutePath().normalize();
		Path legacyPath = null;
		Path backupPath = null;
		try {
			if (Files.exists(destinationPath)) {
				completeMigration(new ConfigMigrationResult(
					ConfigMigrationStatus.SKIPPED_DESTINATION_EXISTS,
					destinationPath,
					null,
					null,
					null
				));
				return MigrationAttempt.SKIPPED;
			}

			legacyPath = migrationSpec.legacyPaths()
				.stream()
				.filter(Files::exists)
				.findFirst()
				.orElse(null);
			if (legacyPath == null) {
				completeMigration(new ConfigMigrationResult(
					ConfigMigrationStatus.SKIPPED_NO_LEGACY_FILE,
					destinationPath,
					null,
					null,
					null
				));
				return MigrationAttempt.SKIPPED;
			}

			backupPath = ConfigFileUtil.backUpFile(legacyPath);
			ConfigMigrationContext context = new ConfigMigrationContext(this);
			try {
				if (migrationSpec.loadsAlternateSource()) {
					context.addValueUpdates(ConfigSerializer.parseMigrationUpdates(legacyPath, categories));
				} else {
					migrationSpec.migrate(legacyPath, context);
				}
			} finally {
				context.close();
			}
			commitMigration(destinationPath, backupPath, context);
			completeMigration(new ConfigMigrationResult(
				ConfigMigrationStatus.MIGRATED,
				destinationPath,
				legacyPath,
				backupPath,
				null
			));
			LOGGER.info("Migrated legacy config file '{}' to '{}'; the source was preserved and backed up at '{}'.", legacyPath, destinationPath, backupPath);
			return MigrationAttempt.MIGRATED;
		} catch (Exception failure) {
			if (failure instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			completeMigration(new ConfigMigrationResult(
				ConfigMigrationStatus.FAILED,
				destinationPath,
				legacyPath,
				backupPath,
				failure
			));
			LOGGER.error("Failed to migrate legacy config file '{}' to '{}'; no migration updates were applied and the source was preserved.", legacyPath, destinationPath, failure);
			return MigrationAttempt.FAILED;
		}
	}

	private void completeMigration(ConfigMigrationResult result) {
		migrationCompleted = true;
		try {
			migrationSpec.onMigrationComplete(result);
		} catch (RuntimeException callbackFailure) {
			LOGGER.error("Failed to handle the completed legacy config migration for '{}'.", id, callbackFailure);
		}
	}

	private void commitMigration(
		Path destinationPath,
		Path backupPath,
		ConfigMigrationContext context
	) throws IOException {
		List<ConfigValueUpdate<?>> valueUpdates = context.getValueUpdates();
		validateUpdates(valueUpdates);
		Map<ConfigValue<?>, Object> updatedValues = getUpdatedValues(valueUpdates);
		List<String> serializedSchema = ConfigSerializer.serializePendingSave(
			categories,
			mode.serializationSettings(),
			updatedValues
		);
		validateProspectiveServerSnapshot(updatedValues);

		List<SortingConfig.MigrationUpdate<?>> sortingUpdates = context.getSortingUpdates();
		Map<Path, List<String>> outputs = new LinkedHashMap<>();
		putMigrationOutput(outputs, destinationPath, serializedSchema);
		for (SortingConfig.MigrationUpdate<?> sortingUpdate : sortingUpdates) {
			putMigrationOutput(outputs, sortingUpdate.path(), sortingUpdate.serialized());
		}
		for (Path legacyPath : migrationSpec.legacyPaths()) {
			if (outputs.containsKey(legacyPath)) {
				throw new IllegalArgumentException("Migration updates must not overwrite a registered legacy file: " + legacyPath);
			}
		}
		if (outputs.containsKey(backupPath)) {
			throw new IllegalArgumentException("Migration updates must not overwrite the legacy file backup: " + backupPath);
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

	void completeInactiveMigration() {
		if (migrationSpec != null && !migrationCompleted) {
			completeMigration(new ConfigMigrationResult(
				ConfigMigrationStatus.SKIPPED_INACTIVE,
				null,
				null,
				null,
				null
			));
		}
	}

	private LoadResult completeServerLoad(
		LoadState previousState,
		List<InitialSave> initialSaves
	) {
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
			if (!registered || registrationInProgress) {
				throw e;
			}
			LOGGER.error(
				"Rejected unsynchronizable server config file state for '{}'; keeping the previous authoritative values.",
				activePath,
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

	private void load(@Nullable Path path) {
		if (path == null || !Files.exists(path)) {
			return;
		}
		try {
			ConfigSerializer.loadWithoutNotifyingUnconditionally(path, categories, mode.serializationSettings());
		} catch (IOException e) {
			handleFileError("load", path, e);
		}
	}

	private static List<InitialSave> getInitialSaves(
		@Nullable Path defaultPath,
		@Nullable Path activePath,
		boolean activePathChanged,
		boolean createActiveFileOnActivation,
		boolean allowActiveInitialSave
	) {
		List<InitialSave> initialSaves = new ArrayList<>(2);
		if (defaultPath != null && !Files.exists(defaultPath)) {
			initialSaves.add(new InitialSave(defaultPath, true));
		}
		if (allowActiveInitialSave && activePath != null && !activePath.equals(defaultPath) && activePathChanged &&
			(createActiveFileOnActivation || defaultPath == null || Files.exists(activePath))
		) {
			initialSaves.add(new InitialSave(activePath, false));
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
		return categories.stream()
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
		if (activePath == null) {
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
		if (Objects.equals(activeDefaultPath, defaultPath) && Objects.equals(activePath, path)) {
			return;
		}
		flushPendingSaveIfNeeded();
		removeFileWatcherCallbacks();
		activeDefaultPath = defaultPath;
		activePath = path;
		if (defaultPath != null && fileWatcher != null) {
			removeDefaultFileWatcherCallback = fileWatcher.addCallback(defaultPath, this::onFileChanged);
		}
		if (path != null && !path.equals(defaultPath) && fileWatcher != null) {
			removeFileWatcherCallback = fileWatcher.addCallback(path, this::onFileChanged);
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
		if (pendingPath != null && Objects.equals(activePath, pendingPath)) {
			save(pendingPath);
		}
	}

	private void onFileChanged() {
		needsLoad.set(true);
		if (isSynchronizedServerSchema()) {
			ServerConfigRuntime.onServerSchemaChanged(this);
		}
	}

	public synchronized void register(@Nullable FileWatcher fileWatcher, boolean logUntranslatedKeys) {
		register(fileWatcher, logUntranslatedKeys, NO_PATH_RESERVATION);
	}

	public synchronized void register(
		@Nullable FileWatcher fileWatcher,
		boolean logUntranslatedKeys,
		Consumer<? super Collection<Path>> pathReservation
	) {
		if (registered) {
			throw new IllegalStateException("Config schema is already registered.");
		}
		this.fileWatcher = fileWatcher;
		this.logUntranslatedKeys = logUntranslatedKeys;
		this.pathReservation = ErrorUtil.checkNotNull(pathReservation, "pathReservation");
		this.registered = true;
		this.registrationInProgress = true;
		try {
			loadIfNeeded();
		} catch (RuntimeException | Error e) {
			rollbackRegistration(e);
			throw e;
		} finally {
			registrationInProgress = false;
		}
	}

	public synchronized void rollbackRegistration(Throwable failure) {
		registered = false;
		registrationInProgress = false;
		fileWatcher = null;
		logUntranslatedKeys = false;
		try {
			removeFileWatcherCallbacks();
		} catch (RuntimeException | Error rollbackFailure) {
			failure.addSuppressed(rollbackFailure);
		}
		try {
			pathReservation.accept(List.of());
		} catch (RuntimeException | Error rollbackFailure) {
			failure.addSuppressed(rollbackFailure);
		}
		pathReservation = NO_PATH_RESERVATION;
		activeDefaultPath = null;
		activePath = null;
		pendingSavePath = null;
		needsLoad.set(true);
		changeVersion.set(0);
		restartValuesInitialized = false;
		translationKeysChecked = false;
		remotelyActive = false;
		resetAllValuesToDefaults();
	}

	private void removeFileWatcherCallbacks() {
		Runnable removeDefaultCallback = removeDefaultFileWatcherCallback;
		Runnable removeCallback = removeFileWatcherCallback;
		removeDefaultFileWatcherCallback = null;
		removeFileWatcherCallback = null;
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

	private synchronized void saveAfterLocalizationLoads(Path path, int attempt) {
		if (!Objects.equals(path, activePath) && !Objects.equals(path, pendingSavePath)) {
			return;
		}
		if (!mode.waitForLocalization() || ConfigSerializer.canLocalizeComments()) {
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
			if (!Objects.equals(path, activeDefaultPath) || Files.exists(path)) {
				return;
			}
		} else if (!Objects.equals(path, activePath)) {
			return;
		}
		if (!mode.waitForLocalization() || ConfigSerializer.canLocalizeComments()) {
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
				write(initialSave.path());
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
		Path defaultPath = activeDefaultPath;
		if (defaultPath != null) {
			saveDefaultIfMissing(defaultPath);
		}
		if (mode.serializationSettings().localizeComments()) {
			logUntranslatedKeysIfNeeded(path);
		}
		ConfigSerializer.save(path, categories, mode.serializationSettings());
	}

	private void handleFileError(String action, Path path, IOException error) {
		if (mode.synchronousFileAccess()) {
			throw new UncheckedIOException("Failed to %s config schema: %s".formatted(action, path), error);
		}
		LOGGER.error("Failed to {} config file: '{}'", action, path, error);
	}

	private void saveDefaultIfMissing(Path path) throws IOException {
		if (!Files.exists(path)) {
			ConfigSerializer.saveDefaults(path, categories, mode.serializationSettings());
		}
	}

	private void logUntranslatedKeysIfNeeded(Path path) {
		if (!logUntranslatedKeys || translationKeysChecked || !ConfigSerializer.canLocalizeComments()) {
			return;
		}
		translationKeysChecked = true;
		ConfigTranslationChecker.logUntranslatedKeys(path, editorCategories, categories);
	}

	public synchronized void logUntranslatedKeysIfReady() {
		Path path = activePath;
		if (path == null) {
			path = activeDefaultPath;
		}
		if (path != null) {
			logUntranslatedKeysIfNeeded(path);
		}
	}

	public synchronized void markDirty() {
		Path path = activePath;
		if (path == null) {
			return;
		}
		pendingSavePath = path;
		delayedSave.run(() -> saveAfterLocalizationLoads(path, 0));
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

		loadIfNeeded();
		if (activePath == null) {
			throw new IllegalStateException("Config schema has no active backing file.");
		}
		validateUpdates(updates);
		Map<ConfigValue<?>, Object> updatedValues = getUpdatedValues(updates);
		ConfigSerializer.validatePendingSave(categories, mode.serializationSettings(), updatedValues);
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
		return categories.stream()
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
		ErrorUtil.checkNotNull(listener, "listener");
		return this.batchListeners.add(listener);
	}

	@Override
	public Runnable addPendingBatchListener(IConfigValueBatchChangeListener listener) {
		ErrorUtil.checkNotNull(listener, "listener");
		return this.pendingBatchListeners.add(listener);
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
		List<IConfigValueBatchChangeListener> pendingSchemaListeners = pendingBatchListeners.snapshot();
		List<IConfigValueBatchChangeListener> effectiveSchemaListeners = batchListeners.snapshot();
		if (!immutablePendingChanges.isEmpty()) {
			pendingNotifications.run();
			notifyListeners(immutablePendingChanges, pendingSchemaListeners, "pending config schema");
		}
		if (!immutableEffectiveChanges.isEmpty()) {
			effectiveNotifications.run();
			notifyListeners(immutableEffectiveChanges, effectiveSchemaListeners, "config schema");
			if (registered && isSynchronizedServerSchema()) {
				ServerConfigRuntime.onServerSchemaChanged(this);
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
				LOGGER.error("{} listener failed for '{}'.", description, activePath, e);
			}
		}
	}

	@Override
	public List<ConfigCategory> getCategories() {
		return categories;
	}

	@Override
	public List<ConfigEditorCategory> getEditorCategories() {
		return editorCategories;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getModId() {
		return modId;
	}

	@Override
	public ConfigSchemaType getType() {
		return type;
	}

	private boolean isSynchronizedServerSchema() {
		return type == ConfigSchemaType.SERVER;
	}

	@Override
	public synchronized boolean isActive() {
		loadIfNeeded();
		return activePath != null || (isSynchronizedServerSchema() && remotelyActive);
	}

	@Override
	public synchronized Optional<Path> getPath() {
		loadIfNeeded();
		return Optional.ofNullable(activePath);
	}

	public synchronized Optional<Path> getRegistrationPath() {
		return pathResolver.resolvePath()
			.map(Path::normalize);
	}

	public synchronized Optional<Path> getDefaultPath() {
		return pathResolver.resolveDefaultPath()
			.map(Path::normalize);
	}

	public ServerConfigKey getServerKey() {
		if (serverKey == null) {
			throw new IllegalStateException("Config schema is not a server schema.");
		}
		return serverKey;
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
		return type != ConfigSchemaType.CLIENT && activePath == null && !remotelyActive;
	}

	public synchronized List<ServerConfigValueData> serializeValues() {
		loadIfNeeded();
		return serializeCurrentValues();
	}

	private List<ServerConfigValueData> serializeCurrentValues() {
		List<ServerConfigValueData> values = new ArrayList<>();
		for (ConfigCategory category : categories) {
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
			getServerKey(),
			serializeProspectiveValues(updatedValues, ServerSnapshotState.CURRENT)
		);
		boolean hasWorldRestartValue = getConfigValues().stream()
			.anyMatch(value -> value.getRestartRequirement() == ConfigValueRestartRequirement.WORLD_RESTART);
		boolean hasGameRestartValue = getConfigValues().stream()
			.anyMatch(value -> value.getRestartRequirement() == ConfigValueRestartRequirement.GAME_RESTART);
		if (hasWorldRestartValue) {
			ServerConfigRuntime.validateSnapshot(
				getServerKey(),
				serializeProspectiveValues(updatedValues, ServerSnapshotState.AFTER_WORLD_RESTART)
			);
		}
		if (hasGameRestartValue) {
			ServerConfigRuntime.validateSnapshot(
				getServerKey(),
				serializeProspectiveValues(updatedValues, ServerSnapshotState.AFTER_GAME_RESTART)
			);
		}
	}

	private List<ServerConfigValueData> serializeProspectiveValues(
		Map<ConfigValue<?>, Object> updatedValues,
		ServerSnapshotState state
	) {
		List<ServerConfigValueData> values = new ArrayList<>();
		for (ConfigCategory category : categories) {
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
			Optional<ConfigCategory> optionalCategory = categories.stream()
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

	public synchronized void applyRemoteSnapshot(List<ServerConfigValueData> values) {
		if (!isSynchronizedServerSchema()) {
			throw new IllegalStateException("Config schema is not server-owned.");
		}
		List<SynchronizedConfigValue<?>> synchronizedValues = new ArrayList<>();
		for (ResolvedServerConfigValue value : resolveServerValues(values, true)) {
			synchronizedValues.add(deserializeSynchronizedValue(value));
		}
		loadIfNeeded();
		if (activePath != null) {
			return;
		}
		Map<ConfigValue<?>, Object> previousEffectiveValues = getEffectiveValues();
		Map<ConfigValue<?>, Object> previousPendingValues = getPendingValues();
		boolean previouslyUsedDeclaredDefaults = usesDeclaredDefaults();
		resetAllValuesToDefaults();
		synchronizedValues.forEach(SynchronizedConfigValue::apply);
		remotelyActive = true;
		needsLoad.set(false);
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

	public synchronized void clearRemoteSnapshot() {
		if (!isSynchronizedServerSchema()) {
			return;
		}
		if (!remotelyActive) {
			return;
		}
		Map<ConfigValue<?>, Object> previousEffectiveValues = getEffectiveValues();
		Map<ConfigValue<?>, Object> previousPendingValues = getPendingValues();
		remotelyActive = false;
		resetAllValuesToDefaults();
		needsLoad.set(true);
		List<AppliedConfigValueChange<?>> effectiveChanges = getEffectiveChanges(previousEffectiveValues);
		List<AppliedConfigValueChange<?>> pendingChanges = getPendingChanges(previousPendingValues);
		notifyChanges(effectiveChanges, pendingChanges);
	}

	private record LoadState(
		Map<ConfigValue<?>, Object> effectiveValues,
		Map<ConfigValue<?>, Object> pendingValues,
		boolean restartValuesInitialized,
		boolean remotelyActive,
		boolean usesDeclaredDefaults
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
		boolean defaults
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
