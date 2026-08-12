package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.IConfigBatchUpdater;
import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.value.IConfigValueBatchChangeListener;
import net.mezzdev.config.api.value.IAppliedConfigValueChange;
import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.file.ConfigSerializer;
import net.mezzdev.config.file.IConfigFileRegistrar;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ConfigSchema implements IConfigSchema {
	private static final Logger LOGGER = LogManager.getLogger();
	static final String DEFAULT_MOD_ID = "mezz_config";
	private static final Duration SAVE_DELAY_TIME = Duration.ofSeconds(2);
	private static final int LOCALIZATION_SAVE_RETRY_LIMIT = 30;

	private final String modId;
	private final ConfigSchemaPathResolver pathResolver;
	private final ConfigSchemaType type;
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
	private @Nullable List<IConfigValueBatchChangeListener> listeners;
	private boolean registered;
	private boolean logUntranslatedKeys;
	private boolean translationKeysChecked;
	private volatile boolean remotelyActive;
	private volatile boolean remoteCanEdit;
	private volatile @Nullable ConfigSchema serverCounterpart;
	private volatile Map<ConfigValue<?>, ConfigValue<?>> serverValueCounterparts = Map.of();

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
		this.serverKey = serverKey;
		if ((type == ConfigSchemaType.SERVER) != (serverKey != null)) {
			throw new IllegalArgumentException("Server config schemas must have exactly one server key.");
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
		this.delayedSave = new DeduplicatingRunner(SAVE_DELAY_TIME, scheduler);
	}

	public static String validateModId(String modId) {
		modId = ErrorUtil.checkNotNull(modId, "modId");
		if (modId.isBlank()) {
			throw new IllegalArgumentException("modId must not be blank.");
		}
		return modId;
	}

	public void loadIfNeeded() {
		LoadResult loadResult = loadIfNeededWithoutNotifying();
		List<AppliedConfigValueChange<?>> changes = loadResult.changes();
		if (!changes.isEmpty()) {
			List<AppliedConfigValueChange<?>> immutableChanges = ConfigValue.notifyChangedValues(changes);
			notifyListeners(immutableChanges);
		}
		InitialSave initialSave = loadResult.initialSave();
		if (registered && initialSave != null) {
			saveInitialFileAfterLocalizationLoads(initialSave, 0);
		}
	}

	private synchronized LoadResult loadIfNeededWithoutNotifying() {
		Map<ConfigValue<?>, Object> previousValues = getCurrentValues();
		Path previousDefaultPath = activeDefaultPath;
		Path previousPath = activePath;
		Path defaultPath = pathResolver.resolveDefaultPath()
			.map(Path::normalize)
			.orElse(null);
		Optional<Path> resolvedPath = pathResolver.resolvePath()
			.map(Path::normalize);
		Path path = resolvedPath.orElse(null);
		if (type == ConfigSchemaType.SERVER && remotelyActive && path == null) {
			setActivePaths(defaultPath, null);
			needsLoad.set(false);
			return new LoadResult(List.of(), null);
		}
		if (path != null) {
			remotelyActive = false;
		}
		boolean defaultPathChanged = !Objects.equals(defaultPath, previousDefaultPath);
		boolean activePathChanged = !Objects.equals(path, previousPath);
		boolean pathsChanged = defaultPathChanged || activePathChanged;
		if (pathsChanged) {
			setActivePaths(defaultPath, path);
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
				return new LoadResult(getChanges(previousValues), initialSave);
			}
			return new LoadResult(List.of(), initialSave);
		}

		if (!needsLoad.compareAndSet(true, false)) {
			if (pathsChanged) {
				return new LoadResult(getChanges(previousValues), null);
			}
			return new LoadResult(List.of(), null);
		}

		resetValuesToDefaults();
		load(defaultPath);
		load(path);
		return new LoadResult(
			getChanges(previousValues),
			getInitialSave(defaultPath, path, activePathChanged, type == ConfigSchemaType.SERVER)
		);
	}

	private void load(@Nullable Path path) {
		if (path == null || !Files.exists(path)) {
			return;
		}
		try {
			ConfigSerializer.loadWithoutNotifyingUnconditionally(path, categories);
		} catch (IOException e) {
			LOGGER.error("Failed to load config schema for: {}", path, e);
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

	private Map<ConfigValue<?>, Object> getCurrentValues() {
		Map<ConfigValue<?>, Object> values = new IdentityHashMap<>();
		getConfigValues().forEach(configValue -> values.put(configValue, configValue.getValueWithoutLoading()));
		return values;
	}

	private List<AppliedConfigValueChange<?>> getChanges(Map<ConfigValue<?>, Object> previousValues) {
		List<AppliedConfigValueChange<?>> changes = new ArrayList<>();
		for (ConfigValue<?> configValue : getConfigValues()) {
			Object oldValue = previousValues.get(configValue);
			AppliedConfigValueChange<?> change = getChange(configValue, oldValue);
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
	private static <T> @Nullable AppliedConfigValueChange<T> getChange(ConfigValue<T> configValue, Object oldValue) {
		T currentValue = configValue.getValueWithoutLoading();
		if (!Objects.equals(oldValue, currentValue)) {
			return new AppliedConfigValueChange<>(configValue, (T) oldValue, currentValue);
		}
		return null;
	}

	private void resetValuesToDefaults() {
		getConfigValues().forEach(ConfigValue::resetToDefaultWithoutNotifying);
	}

	private void setActivePaths(@Nullable Path defaultPath, @Nullable Path path) {
		if (Objects.equals(activeDefaultPath, defaultPath) && Objects.equals(activePath, path)) {
			return;
		}
		flushPendingSaveIfNeeded();
		if (removeDefaultFileWatcherCallback != null) {
			removeDefaultFileWatcherCallback.run();
			removeDefaultFileWatcherCallback = null;
		}
		if (removeFileWatcherCallback != null) {
			removeFileWatcherCallback.run();
			removeFileWatcherCallback = null;
		}
		activeDefaultPath = defaultPath;
		activePath = path;
		if (defaultPath != null && fileWatcher != null) {
			removeDefaultFileWatcherCallback = fileWatcher.addCallback(defaultPath, this::onFileChanged);
		}
		if (path != null && !path.equals(defaultPath) && fileWatcher != null) {
			removeFileWatcherCallback = fileWatcher.addCallback(path, this::onFileChanged);
		}
	}

	private void flushPendingSaveIfNeeded() {
		Path pendingPath = pendingSavePath;
		if (pendingPath != null && Objects.equals(activePath, pendingPath)) {
			save(pendingPath);
		}
	}

	private void onFileChanged() {
		needsLoad.set(true);
	}

	public void register(
		@Nullable FileWatcher fileWatcher,
		IConfigFileRegistrar configFileRegistrar,
		boolean logUntranslatedKeys
	) {
		this.fileWatcher = fileWatcher;
		this.logUntranslatedKeys = logUntranslatedKeys;
		this.registered = true;
		loadIfNeeded();
		configFileRegistrar.addConfigFile(this);
	}

	private void saveAfterLocalizationLoads(Path path, int attempt) {
		if (!Objects.equals(path, activePath) && !Objects.equals(path, pendingSavePath)) {
			return;
		}
		if (type == ConfigSchemaType.SERVER || ConfigSerializer.canLocalizeComments()) {
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

	private void saveInitialFileAfterLocalizationLoads(InitialSave initialSave, int attempt) {
		Path path = initialSave.path();
		if (initialSave.defaults()) {
			if (!Objects.equals(path, activeDefaultPath) || Files.exists(path)) {
				return;
			}
		} else if (!Objects.equals(path, activePath)) {
			return;
		}
		if (type == ConfigSchemaType.SERVER || ConfigSerializer.canLocalizeComments()) {
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

	private void saveInitialFile(InitialSave initialSave) {
		if (initialSave.defaults()) {
			try {
				saveDefaultIfMissing(initialSave.path());
			} catch (IOException e) {
				LOGGER.error("Failed to save default config file: '{}'", initialSave.path(), e);
			}
		} else {
			save(initialSave.path());
		}
	}

	private synchronized void save(Path path) {
		try {
			Path defaultPath = activeDefaultPath;
			if (defaultPath != null) {
				saveDefaultIfMissing(defaultPath);
			}
			logUntranslatedKeysIfNeeded(path);
			ConfigSerializer.save(path, categories);
		} catch (IOException e) {
			LOGGER.error("Failed to save config file: '{}'", path, e);
		} finally {
			if (Objects.equals(pendingSavePath, path)) {
				pendingSavePath = null;
			}
		}
	}

	private void saveDefaultIfMissing(Path path) throws IOException {
		if (!Files.exists(path)) {
			ConfigSerializer.saveDefaults(path, categories);
		}
	}

	private void logUntranslatedKeysIfNeeded(Path path) {
		if (!logUntranslatedKeys || translationKeysChecked || !ConfigSerializer.canLocalizeComments()) {
			return;
		}
		translationKeysChecked = true;
		ConfigTranslationChecker.logUntranslatedKeys(path, editorCategories, categories);
	}

	public void markDirty() {
		Path path = activePath;
		if (path == null) {
			return;
		}
		pendingSavePath = path;
		delayedSave.run(() -> saveAfterLocalizationLoads(path, 0));
	}

	@Override
	public List<? extends IAppliedConfigValueChange<?>> batchUpdate(Consumer<IConfigBatchUpdater> updateBatch) {
		if (type == ConfigSchemaType.SERVER) {
			throw new IllegalStateException("Server config schemas must be updated through requestBatchUpdate.");
		}
		ConfigBatchUpdater updater = createBatchUpdater(updateBatch);
		return applyBatchUpdates(updater.getUpdates());
	}

	@Override
	public CompletableFuture<Void> requestBatchUpdate(Consumer<IConfigBatchUpdater> updateBatch) {
		ConfigBatchUpdater updater = createBatchUpdater(updateBatch);
		List<ConfigValueUpdate<?>> updates = updater.getUpdates();
		if (type != ConfigSchemaType.SERVER) {
			applyBatchUpdates(updates);
			return CompletableFuture.completedFuture(null);
		}
		ConfigSchema activeServerCounterpart = getActiveServerCounterpart();
		if (activeServerCounterpart != null) {
			validateUpdates(updates);
			if (updates.isEmpty()) {
				return CompletableFuture.completedFuture(null);
			}
			List<ConfigValueUpdate<?>> serverUpdates = activeServerCounterpart.deserializeUpdates(serializeUpdates(updates), false);
			return ServerConfigRuntime.requestLocalUpdate(activeServerCounterpart, serverUpdates);
		}
		loadIfNeeded();
		if (!isActive()) {
			throw new IllegalStateException("Server config schema is not active.");
		}
		validateUpdates(updates);
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

	List<AppliedConfigValueChange<?>> applyBatchUpdates(List<? extends ConfigValueUpdate<?>> updates) {
		ErrorUtil.checkNotNull(updates, "updates");
		if (updates.isEmpty()) {
			return List.of();
		}

		loadIfNeeded();
		if (activePath == null) {
			throw new IllegalStateException("Config schema has no active backing file.");
		}
		validateUpdates(updates);

		List<AppliedConfigValueChange<?>> changes = applyUpdatesAtomically(updates);
		if (changes.isEmpty()) {
			return List.of();
		}

		markDirty();
		List<AppliedConfigValueChange<?>> immutableChanges = ConfigValue.notifyChangedValues(changes);
		notifyListeners(immutableChanges);
		return immutableChanges;
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
		return new ConfigValueUpdate<>(configValue, configValue.getValueWithoutLoading());
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
	public Runnable addListener(IConfigValueBatchChangeListener listener) {
		ErrorUtil.checkNotNull(listener, "listener");
		if (this.listeners == null) {
			this.listeners = new ArrayList<>();
		}
		this.listeners.add(listener);
		return () -> {
			if (this.listeners != null) {
				this.listeners.remove(listener);
			}
		};
	}

	private void notifyListeners(List<? extends IAppliedConfigValueChange<?>> changes) {
		if (!changes.isEmpty()) {
			changeVersion.incrementAndGet();
		}
		if (listeners != null && !changes.isEmpty()) {
			List<IConfigValueBatchChangeListener> listeners = List.copyOf(this.listeners);
			for (IConfigValueBatchChangeListener listener : listeners) {
				try {
					listener.onChange(changes);
				} catch (RuntimeException e) {
					LOGGER.error("Config schema listener failed for '{}'.", activePath, e);
				}
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

	@Override
	public boolean isActive() {
		ConfigSchema activeServerCounterpart = getActiveServerCounterpart();
		if (activeServerCounterpart != null) {
			return activeServerCounterpart.isActive();
		}
		loadIfNeeded();
		return activePath != null || (type == ConfigSchemaType.SERVER && remotelyActive);
	}

	@Override
	public boolean canEdit() {
		ConfigSchema activeServerCounterpart = getActiveServerCounterpart();
		if (activeServerCounterpart != null) {
			return activeServerCounterpart.isActive();
		}
		if (type == ConfigSchemaType.SERVER) {
			return isActive() && remoteCanEdit;
		}
		return isActive();
	}

	@Override
	public Optional<Path> getPath() {
		ConfigSchema activeServerCounterpart = getActiveServerCounterpart();
		if (activeServerCounterpart != null) {
			return activeServerCounterpart.getPath();
		}
		loadIfNeeded();
		return Optional.ofNullable(activePath);
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

	public synchronized void linkServerCounterpart(ConfigSchema serverCounterpart) {
		serverCounterpart = ErrorUtil.checkNotNull(serverCounterpart, "serverCounterpart");
		if (type != ConfigSchemaType.SERVER || serverCounterpart.type != ConfigSchemaType.SERVER) {
			throw new IllegalArgumentException("Only server config schemas can be linked across logical sides.");
		}
		if (!getServerKey().equals(serverCounterpart.getServerKey())) {
			throw new IllegalArgumentException("Linked server config schemas must have the same key.");
		}
		Map<ConfigValue<?>, ConfigValue<?>> valueCounterparts = new IdentityHashMap<>();
		for (ConfigCategory category : categories) {
			ConfigCategory serverCategory = serverCounterpart.categories.stream()
				.filter(candidate -> candidate.getName().equals(category.getName()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Server schema is missing config category: " + category.getName()));
			for (ConfigValue<?> value : category.getConfigValues()) {
				ConfigValue<?> serverValue = serverCategory.getConfigValue(value.getName())
					.orElseThrow(() -> new IllegalArgumentException("Server schema is missing config value: " + category.getName() + "." + value.getName()));
				valueCounterparts.put(value, serverValue);
			}
		}
		if (valueCounterparts.size() != serverCounterpart.getConfigValues().size()) {
			throw new IllegalArgumentException("Linked server config schemas have different config values.");
		}
		this.serverValueCounterparts = Map.copyOf(valueCounterparts);
		this.serverCounterpart = serverCounterpart;
	}

	public <T> T getEffectiveValue(ConfigValue<T> configValue) {
		ConfigSchema activeServerCounterpart = getActiveServerCounterpart();
		if (activeServerCounterpart == null) {
			loadIfNeeded();
			return configValue.getValueWithoutLoading();
		}
		@SuppressWarnings("unchecked")
		ConfigValue<T> serverValue = (ConfigValue<T>) serverValueCounterparts.get(configValue);
		if (serverValue == null) {
			throw new IllegalArgumentException("Config value does not belong to this schema: " + configValue.getName());
		}
		activeServerCounterpart.loadIfNeeded();
		return serverValue.getValueWithoutLoading();
	}

	private @Nullable ConfigSchema getActiveServerCounterpart() {
		ConfigSchema serverCounterpart = this.serverCounterpart;
		if (serverCounterpart != null && ServerConfigRuntime.isServerThread()) {
			return serverCounterpart;
		}
		return null;
	}

	public List<ServerConfigValueData> serializeValues() {
		loadIfNeeded();
		List<ServerConfigValueData> values = new ArrayList<>();
		for (ConfigCategory category : categories) {
			for (ConfigValue<?> value : category.getConfigValues()) {
				values.add(serializeValue(category.getName(), value, value.getValueWithoutLoading()));
			}
		}
		return List.copyOf(values);
	}

	public List<ServerConfigValueData> serializeUpdates(List<? extends ConfigValueUpdate<?>> updates) {
		validateUpdates(updates);
		List<ServerConfigValueData> values = new ArrayList<>();
		for (ConfigValueUpdate<?> update : updates) {
			String categoryName = getCategoryName(update.configValue());
			values.add(serializeValue(categoryName, update.configValue(), update.newValue()));
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

	private static <T> ServerConfigValueData serializeValue(String categoryName, ConfigValue<T> value, Object rawValue) {
		@SuppressWarnings("unchecked")
		T typedValue = (T) rawValue;
		return new ServerConfigValueData(categoryName, value.getName(), value.getSerializer().serialize(typedValue));
	}

	public List<ConfigValueUpdate<?>> deserializeUpdates(List<ServerConfigValueData> values, boolean allowSchemaDifferences) {
		ErrorUtil.checkNotNull(values, "values");
		Set<ConfigValue<?>> updatedValues = new HashSet<>();
		List<ConfigValueUpdate<?>> updates = new ArrayList<>();
		for (ServerConfigValueData value : values) {
			Optional<ConfigCategory> optionalCategory = categories.stream()
				.filter(candidate -> candidate.getName().equals(value.categoryName()))
				.findFirst();
			if (optionalCategory.isEmpty()) {
				if (allowSchemaDifferences) {
					continue;
				}
				throw new IllegalArgumentException("Unknown config category: " + value.categoryName());
			}
			Optional<ConfigValue<?>> optionalConfigValue = optionalCategory.orElseThrow()
				.getConfigValue(value.valueName());
			if (optionalConfigValue.isEmpty()) {
				if (allowSchemaDifferences) {
					continue;
				}
				throw new IllegalArgumentException("Unknown config value: " + value.categoryName() + "." + value.valueName());
			}
			ConfigValue<?> configValue = optionalConfigValue.orElseThrow();
			if (!updatedValues.add(configValue)) {
				throw new IllegalArgumentException("Config value was provided more than once: " + value.categoryName() + "." + value.valueName());
			}
			updates.add(deserializeUpdate(configValue, value.serializedValue()));
		}
		return List.copyOf(updates);
	}

	private static <T> ConfigValueUpdate<T> deserializeUpdate(ConfigValue<T> configValue, String serializedValue) {
		IDeserializeResult<T> result = configValue.getSerializer().deserialize(serializedValue);
		if (!result.getDiagnostics().isEmpty() || result.getResult().isEmpty()) {
			String diagnostics = String.join("; ", result.getDiagnostics());
			throw new IllegalArgumentException("Invalid value for '%s': %s".formatted(configValue.getName(), diagnostics));
		}
		return new ConfigValueUpdate<>(configValue, result.getResult().orElseThrow());
	}

	public List<AppliedConfigValueChange<?>> applyServerUpdates(List<? extends ConfigValueUpdate<?>> updates) {
		if (type != ConfigSchemaType.SERVER) {
			throw new IllegalStateException("Config schema is not server-owned.");
		}
		return applyBatchUpdates(updates);
	}

	public synchronized void applyRemoteSnapshot(List<ServerConfigValueData> values, boolean canEdit) {
		if (type != ConfigSchemaType.SERVER) {
			throw new IllegalStateException("Config schema is not server-owned.");
		}
		List<ConfigValueUpdate<?>> updates = deserializeUpdates(values, true);
		loadIfNeeded();
		if (activePath != null) {
			remoteCanEdit = canEdit;
			return;
		}
		Map<ConfigValue<?>, ConfigValueUpdate<?>> desiredUpdates = new IdentityHashMap<>();
		for (ConfigValue<?> configValue : getConfigValues()) {
			desiredUpdates.put(configValue, createDefaultUpdate(configValue));
		}
		for (ConfigValueUpdate<?> update : updates) {
			desiredUpdates.put(update.configValue(), update);
		}
		List<ConfigValueUpdate<?>> orderedUpdates = new ArrayList<>();
		for (ConfigValue<?> configValue : getConfigValues()) {
			orderedUpdates.add(desiredUpdates.get(configValue));
		}
		List<AppliedConfigValueChange<?>> changes = applyUpdatesAtomically(orderedUpdates);
		remoteCanEdit = canEdit;
		remotelyActive = true;
		needsLoad.set(false);
		if (!changes.isEmpty()) {
			List<AppliedConfigValueChange<?>> immutableChanges = ConfigValue.notifyChangedValues(changes);
			notifyListeners(immutableChanges);
		}
	}

	private static <T> ConfigValueUpdate<T> createDefaultUpdate(ConfigValue<T> configValue) {
		return new ConfigValueUpdate<>(configValue, configValue.getDefaultValue());
	}

	public synchronized void clearRemoteSnapshot() {
		if (type != ConfigSchemaType.SERVER) {
			return;
		}
		remoteCanEdit = false;
		if (!remotelyActive) {
			return;
		}
		Map<ConfigValue<?>, Object> previousValues = getCurrentValues();
		remotelyActive = false;
		resetValuesToDefaults();
		needsLoad.set(true);
		List<AppliedConfigValueChange<?>> changes = getChanges(previousValues);
		if (!changes.isEmpty()) {
			List<AppliedConfigValueChange<?>> immutableChanges = ConfigValue.notifyChangedValues(changes);
			notifyListeners(immutableChanges);
		}
	}

	private record LoadResult(
		List<AppliedConfigValueChange<?>> changes,
		@Nullable InitialSave initialSave
	) {}

	private record InitialSave(
		Path path,
		boolean defaults
	) {}
}
