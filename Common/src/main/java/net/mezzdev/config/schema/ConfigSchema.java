package net.mezzdev.config.schema;

import com.google.gson.JsonElement;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.IConfigBatchUpdater;
import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.value.IAppliedConfigValueChange;
import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.mezzdev.config.file.ConfigFileValueAdapter;
import net.mezzdev.config.file.ConfigFileValueCodec;
import net.mezzdev.config.file.ConfigSerializer;
import net.mezzdev.config.server.ServerConfigKey;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.server.ServerConfigValueData;
import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.value.AppliedConfigValueChange;
import net.mezzdev.config.value.ConfigValueUpdate;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ConfigSchema implements IConfigSchema {
	private static final Logger LOGGER = LogManager.getLogger();
	static final String DEFAULT_MOD_ID = "mezz_config";
	private static final Duration SAVE_DELAY_TIME = Duration.ofSeconds(2);
	private static final int LOCALIZATION_SAVE_RETRY_LIMIT = 30;
	private static final Consumer<? super Collection<Path>> NO_PATH_RESERVATION = ignored -> {};

	private final String modId;
	private final ConfigSchemaPathResolver pathResolver;
	private final ConfigSchemaType type;
	private final ConfigSchemaMode mode;
	private final @Nullable ServerConfigKey serverKey;
	private final List<ConfigCategory> categories;
	private final List<ConfigEditorCategory> editorCategories;
	private final AtomicBoolean needsLoad = new AtomicBoolean(true);
	private final AtomicLong changeVersion = new AtomicLong();
	private final DeduplicatingRunner delayedSave;
	private @Nullable FileWatcher fileWatcher;
	private @Nullable Path activeDefaultPath;
	private @Nullable Path activePath;
	private @Nullable Path pendingSavePath;
	private @Nullable Runnable removeDefaultFileWatcherCallback;
	private @Nullable Runnable removeFileWatcherCallback;
	private final List<Consumer<? super List<? extends IAppliedConfigValueChange<?>>>> batchListeners = new CopyOnWriteArrayList<>();
	private final List<Consumer<? super List<? extends IAppliedConfigValueChange<?>>>> pendingBatchListeners = new CopyOnWriteArrayList<>();
	private boolean registered;
	private boolean registrationInProgress;
	private boolean restartValuesInitialized;
	private boolean logUntranslatedKeys;
	private boolean translationKeysChecked;
	private volatile boolean remotelyActive;
	private volatile boolean remoteCanEdit;
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
		this.modId = validateModId(modId);
		this.pathResolver = ErrorUtil.checkNotNull(pathResolver, "pathResolver");
		this.type = ErrorUtil.checkNotNull(type, "type");
		this.mode = ConfigSchemaMode.forSchema(type);
		this.serverKey = serverKey;
		validateServerKey(type, serverKey);
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
		this.delayedSave = new DeduplicatingRunner(SAVE_DELAY_TIME, scheduler);
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
		InitialSave initialSave = loadResult.initialSave();
		if (registered && initialSave != null) {
			if (mode.synchronousFileAccess()) {
				saveInitialFile(initialSave);
			} else {
				saveInitialFileAfterLocalizationLoads(initialSave, 0);
			}
		}
	}

	private synchronized LoadResult loadIfNeededWithoutNotifying() {
		Map<ConfigValue<?>, Object> previousEffectiveValues = getEffectiveValues();
		Map<ConfigValue<?>, Object> previousPendingValues = getPendingValues();
		boolean previousRestartValuesInitialized = restartValuesInitialized;
		boolean previousRemotelyActive = remotelyActive;
		boolean previousRemoteCanEdit = remoteCanEdit;
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
			return createLoadResult(previousEffectiveValues, previousPendingValues, null);
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
			InitialSave initialSave = null;
			if (shouldInitializeDefault) {
				initialSave = getInitialSave(defaultPath, null, activePathChanged, false);
			}
			if (previousPath != null) {
				resetValuesToDefaults();
				return completeServerLoad(
					previousEffectiveValues,
					previousPendingValues,
					previousRestartValuesInitialized,
					previousRemotelyActive,
					previousRemoteCanEdit,
					initialSave
				);
			}
			if (shouldInitializeDefault) {
				return completeServerLoad(
					previousEffectiveValues,
					previousPendingValues,
					previousRestartValuesInitialized,
					previousRemotelyActive,
					previousRemoteCanEdit,
					initialSave
				);
			}
			return createLoadResult(previousEffectiveValues, previousPendingValues, initialSave);
		}

		if (!needsLoad.compareAndSet(true, false)) {
			if (pathsChanged) {
				return createLoadResult(previousEffectiveValues, previousPendingValues, null);
			}
			return createLoadResult(previousEffectiveValues, previousPendingValues, null);
		}

		resetValuesToDefaults();
		load(defaultPath);
		load(path);
		if (!restartValuesInitialized) {
			promotePendingValuesWithoutNotifying(ConfigValueRestartRequirement.GAME_RESTART);
			restartValuesInitialized = true;
		}
		return completeServerLoad(
			previousEffectiveValues,
			previousPendingValues,
			previousRestartValuesInitialized,
			previousRemotelyActive,
			previousRemoteCanEdit,
			getInitialSave(defaultPath, path, activePathChanged, isSynchronizedServerSchema())
		);
	}

	private LoadResult completeServerLoad(
		Map<ConfigValue<?>, Object> previousEffectiveValues,
		Map<ConfigValue<?>, Object> previousPendingValues,
		boolean previousRestartValuesInitialized,
		boolean previousRemotelyActive,
		boolean previousRemoteCanEdit,
		@Nullable InitialSave initialSave
	) {
		if (!isSynchronizedServerSchema()) {
			return createLoadResult(previousEffectiveValues, previousPendingValues, initialSave);
		}
		try {
			validateCurrentServerSnapshot();
			return createLoadResult(previousEffectiveValues, previousPendingValues, initialSave);
		} catch (RuntimeException e) {
			restoreValues(previousEffectiveValues, previousPendingValues);
			restartValuesInitialized = previousRestartValuesInitialized;
			remotelyActive = previousRemotelyActive;
			remoteCanEdit = previousRemoteCanEdit;
			if (!registered || registrationInProgress) {
				throw e;
			}
			LOGGER.error(
				"Rejected unsynchronizable server config file state for '{}'; keeping the previous authoritative values.",
				activePath,
				e
			);
			return createLoadResult(previousEffectiveValues, previousPendingValues, null);
		}
	}

	private LoadResult createLoadResult(
		Map<ConfigValue<?>, Object> previousEffectiveValues,
		Map<ConfigValue<?>, Object> previousPendingValues,
		@Nullable InitialSave initialSave
	) {
		return new LoadResult(
			getEffectiveChanges(previousEffectiveValues),
			getPendingChanges(previousPendingValues),
			initialSave
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

	private static @Nullable InitialSave getInitialSave(
		@Nullable Path defaultPath,
		@Nullable Path activePath,
		boolean activePathChanged,
		boolean createActiveFileOnActivation
	) {
		if (defaultPath != null && !Files.exists(defaultPath)) {
			return new InitialSave(defaultPath, true);
		}
		if (activePath != null && activePathChanged &&
			(createActiveFileOnActivation || defaultPath == null || Files.exists(activePath))
		) {
			return new InitialSave(activePath, false);
		}
		return null;
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
		List<AppliedConfigValueChange<?>> changes = new ArrayList<>();
		for (ConfigValue<?> configValue : getConfigValues()) {
			Object oldValue = previousValues.get(configValue);
			AppliedConfigValueChange<?> change = getChange(configValue, oldValue, pending);
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
		boolean pending
	) {
		T currentValue;
		if (pending) {
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
			ServerConfigRuntime.onServerSchemaFileChanged(this);
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
		remoteCanEdit = false;
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
		if (isSynchronizedServerSchema()) {
			throw new IllegalStateException("Server config schemas must be updated through requestBatchUpdate.");
		}
		ConfigBatchUpdater updater = createBatchUpdater(updateBatch);
		return applyBatchUpdates(updater.getUpdates());
	}

	@Override
	public synchronized CompletableFuture<Void> requestBatchUpdate(Consumer<IConfigBatchUpdater> updateBatch) {
		ConfigBatchUpdater updater = createBatchUpdater(updateBatch);
		List<ConfigValueUpdate<?>> updates = updater.getUpdates();
		if (!isSynchronizedServerSchema()) {
			applyBatchUpdates(updates);
			return CompletableFuture.completedFuture(null);
		}
		loadIfNeeded();
		if (!isActive()) {
			throw new IllegalStateException("Server config schema is not active.");
		}
		validateUpdates(updates);
		validateProspectiveServerSnapshot(updates);
		if (updates.isEmpty()) {
			return CompletableFuture.completedFuture(null);
		}
		if (activePath != null) {
			return ServerConfigRuntime.requestLocalUpdate(this, updates);
		}
		return ServerConfigRuntime.requestUpdate(this, updates);
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
		validateProspectiveServerSnapshot(updates);

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
		} catch (RuntimeException e) {
			for (int i = rollbacks.size() - 1; i >= 0; i--) {
				try {
					rollbacks.get(i).apply();
				} catch (RuntimeException rollbackFailure) {
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

	private boolean containsConfigValue(ConfigValue<?> configValue) {
		return categories.stream()
			.flatMap(category -> category.getConfigValues().stream())
			.anyMatch(value -> value == configValue);
	}

	@Override
	public Runnable addBatchListener(Consumer<? super List<? extends IAppliedConfigValueChange<?>>> listener) {
		ErrorUtil.checkNotNull(listener, "listener");
		this.batchListeners.add(listener);
		return () -> this.batchListeners.remove(listener);
	}

	@Override
	public Runnable addPendingBatchListener(Consumer<? super List<? extends IAppliedConfigValueChange<?>>> listener) {
		ErrorUtil.checkNotNull(listener, "listener");
		this.pendingBatchListeners.add(listener);
		return () -> this.pendingBatchListeners.remove(listener);
	}

	private void notifyChanges(
		List<? extends AppliedConfigValueChange<?>> effectiveChanges,
		List<? extends AppliedConfigValueChange<?>> pendingChanges
	) {
		if (effectiveChanges.isEmpty() && pendingChanges.isEmpty()) {
			return;
		}
		changeVersion.incrementAndGet();
		if (!pendingChanges.isEmpty()) {
			List<AppliedConfigValueChange<?>> immutableChanges = ConfigValue.notifyPendingChangedValues(pendingChanges);
			notifyListeners(immutableChanges, pendingBatchListeners, "pending config schema");
		}
		if (!effectiveChanges.isEmpty()) {
			List<AppliedConfigValueChange<?>> immutableChanges = ConfigValue.notifyChangedValues(effectiveChanges);
			notifyListeners(immutableChanges, batchListeners, "config schema");
		}
	}

	private void notifyListeners(
		List<? extends IAppliedConfigValueChange<?>> changes,
		List<Consumer<? super List<? extends IAppliedConfigValueChange<?>>>> registeredListeners,
		String description
	) {
		for (Consumer<? super List<? extends IAppliedConfigValueChange<?>>> listener : registeredListeners) {
			try {
				listener.accept(changes);
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
	public synchronized boolean canEdit() {
		if (isSynchronizedServerSchema()) {
			return isActive() && (activePath != null || remoteCanEdit);
		}
		return isActive();
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
		if (type != ConfigSchemaType.CLIENT && activePath == null && !remotelyActive) {
			return configValue.getDefaultValue();
		}
		return configValue.getEffectiveValueWithoutLoading();
	}

	public synchronized <T> T getPendingValue(ConfigValue<T> configValue) {
		loadIfNeeded();
		if (type != ConfigSchemaType.CLIENT && activePath == null && !remotelyActive) {
			return configValue.getDefaultValue();
		}
		return configValue.getPendingValueWithoutLoading();
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

	private void validateProspectiveServerSnapshot(List<? extends ConfigValueUpdate<?>> updates) {
		if (!isSynchronizedServerSchema()) {
			return;
		}
		Map<ConfigValue<?>, Object> updatedValues = new IdentityHashMap<>();
		updates.forEach(update -> updatedValues.put(update.configValue(), update.newValue()));
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
			ConfigFileValueAdapter.serialize(value.getSerializer(), effectiveValue),
			ConfigFileValueAdapter.serialize(value.getSerializer(), pendingValue)
		);
	}

	public synchronized List<ServerConfigValueData> serializeUpdates(List<? extends ConfigValueUpdate<?>> updates) {
		validateUpdates(updates);
		List<ServerConfigValueData> values = new ArrayList<>();
		for (ConfigValueUpdate<?> update : updates) {
			String categoryName = getCategoryName(update.configValue());
			values.add(serializeUpdate(categoryName, update.configValue(), update.newValue()));
		}
		return List.copyOf(values);
	}

	private String getCategoryName(ConfigValue<?> configValue) {
		for (ConfigCategory category : categories) {
			if (category.getConfigValues().stream().anyMatch(value -> value == configValue)) {
				return category.getName();
			}
		}
		throw new IllegalArgumentException("Config value does not belong to this schema: " + configValue.getName());
	}

	private static <T> ServerConfigValueData serializeValue(String categoryName, ConfigValue<T> value) {
		return new ServerConfigValueData(
			categoryName,
			value.getName(),
			ConfigFileValueAdapter.serialize(value.getSerializer(), value.getEffectiveValueWithoutLoading()),
			ConfigFileValueAdapter.serialize(value.getSerializer(), value.getPendingValueWithoutLoading())
		);
	}

	private static <T> ServerConfigValueData serializeUpdate(String categoryName, ConfigValue<T> value, Object rawValue) {
		@SuppressWarnings("unchecked")
		T typedValue = (T) rawValue;
		return new ServerConfigValueData(
			categoryName,
			value.getName(),
			ConfigFileValueAdapter.serialize(value.getSerializer(), typedValue)
		);
	}

	public synchronized List<ConfigValueUpdate<?>> deserializeUpdates(List<ServerConfigValueData> values, boolean allowSchemaDifferences) {
		List<ConfigValueUpdate<?>> updates = new ArrayList<>();
		for (ResolvedServerConfigValue value : resolveServerValues(values, allowSchemaDifferences)) {
			updates.add(deserializeUpdate(value.configValue(), value.data().serializedPendingValue()));
		}
		return List.copyOf(updates);
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

	private static <T> ConfigValueUpdate<T> deserializeUpdate(ConfigValue<T> configValue, String serializedValue) {
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
		return new ConfigValueUpdate<>(configValue, result.getResult().orElseThrow());
	}

	public synchronized List<AppliedConfigValueChange<?>> applyServerUpdates(List<? extends ConfigValueUpdate<?>> updates) {
		if (!isSynchronizedServerSchema()) {
			throw new IllegalStateException("Config schema is not server-owned.");
		}
		return applyBatchUpdates(updates);
	}

	public synchronized void applyRemoteSnapshot(List<ServerConfigValueData> values, boolean canEdit) {
		if (!isSynchronizedServerSchema()) {
			throw new IllegalStateException("Config schema is not server-owned.");
		}
		List<SynchronizedConfigValue<?>> synchronizedValues = new ArrayList<>();
		for (ResolvedServerConfigValue value : resolveServerValues(values, true)) {
			synchronizedValues.add(deserializeSynchronizedValue(value));
		}
		loadIfNeeded();
		if (activePath != null) {
			remoteCanEdit = canEdit;
			return;
		}
		Map<ConfigValue<?>, Object> previousEffectiveValues = getEffectiveValues();
		Map<ConfigValue<?>, Object> previousPendingValues = getPendingValues();
		resetAllValuesToDefaults();
		synchronizedValues.forEach(SynchronizedConfigValue::apply);
		remoteCanEdit = canEdit;
		remotelyActive = true;
		needsLoad.set(false);
		List<AppliedConfigValueChange<?>> effectiveChanges = getEffectiveChanges(previousEffectiveValues);
		List<AppliedConfigValueChange<?>> pendingChanges = getPendingChanges(previousPendingValues);
		notifyChanges(effectiveChanges, pendingChanges);
	}

	private static <T> SynchronizedConfigValue<T> deserializeSynchronizedValue(ResolvedServerConfigValue value) {
		@SuppressWarnings("unchecked")
		ConfigValue<T> configValue = (ConfigValue<T>) value.configValue();
		T effectiveValue = deserializeValue(configValue, value.data().serializedEffectiveValue());
		T pendingValue = deserializeValue(configValue, value.data().serializedPendingValue());
		return new SynchronizedConfigValue<>(configValue, effectiveValue, pendingValue);
	}

	private static <T> T deserializeValue(ConfigValue<T> configValue, String serializedValue) {
		return deserializeUpdate(configValue, serializedValue).newValue();
	}

	public synchronized void clearRemoteSnapshot() {
		if (!isSynchronizedServerSchema()) {
			return;
		}
		remoteCanEdit = false;
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

	private record LoadResult(
		List<AppliedConfigValueChange<?>> effectiveChanges,
		List<AppliedConfigValueChange<?>> pendingChanges,
		@Nullable InitialSave initialSave
	) {}

	private record ResolvedServerConfigValue(
		ConfigValue<?> configValue,
		ServerConfigValueData data
	) {}

	private record SynchronizedConfigValue<T>(
		ConfigValue<T> configValue,
		T effectiveValue,
		T pendingValue
	) {
		private void apply() {
			configValue.setSynchronizedValuesWithoutNotifying(effectiveValue, pendingValue);
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
}
