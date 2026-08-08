package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.IConfigBatchUpdater;
import net.mezzdev.config.api.value.IConfigValueBatchChangeListener;
import net.mezzdev.config.api.value.IAppliedConfigValueChange;
import net.mezzdev.config.file.ConfigSerializer;
import net.mezzdev.config.file.IConfigFileRegistrar;
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
import java.util.function.Consumer;

public class ConfigSchema implements IConfigSchema {
	private static final Logger LOGGER = LogManager.getLogger();
	static final String DEFAULT_MOD_ID = "mezz_config";
	private static final Duration SAVE_DELAY_TIME = Duration.ofSeconds(2);
	private static final int LOCALIZATION_SAVE_RETRY_LIMIT = 30;

	private final String modId;
	private final ConfigSchemaPathResolver pathResolver;
	private final List<ConfigCategory> categories;
	private final List<ConfigEditorCategory> editorCategories;
	private final AtomicBoolean needsLoad = new AtomicBoolean(true);
	private final DeduplicatingRunner delayedSave;
	private @Nullable FileWatcher fileWatcher;
	private @Nullable Path activePath;
	private @Nullable Path pendingSavePath;
	private @Nullable Runnable removeFileWatcherCallback;
	private @Nullable List<IConfigValueBatchChangeListener> listeners;
	private boolean registered;
	private boolean logUntranslatedKeys;
	private boolean translationKeysChecked;

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
		this.modId = validateModId(modId);
		this.pathResolver = ErrorUtil.checkNotNull(pathResolver, "pathResolver");
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
		if (loadResult.activePathChanged() && registered && activePath != null) {
			saveAfterLocalizationLoads(activePath, 0);
		}
	}

	private synchronized LoadResult loadIfNeededWithoutNotifying() {
		Map<ConfigValue<?>, Object> previousValues = getCurrentValues();
		Path previousPath = activePath;
		Optional<Path> resolvedPath = pathResolver.resolvePath()
			.map(Path::normalize);
		if (resolvedPath.isEmpty()) {
			if (previousPath != null) {
				setActivePath(null);
				resetValuesToDefaults();
				needsLoad.set(true);
				return new LoadResult(getChanges(previousValues), true);
			}
			return LoadResult.EMPTY;
		}

		Path path = resolvedPath.get();
		boolean pathChanged = !path.equals(previousPath);
		if (pathChanged) {
			setActivePath(path);
			resetValuesToDefaults();
			needsLoad.set(true);
		}

		if (!needsLoad.compareAndSet(true, false)) {
			if (pathChanged) {
				return new LoadResult(getChanges(previousValues), true);
			}
			return LoadResult.EMPTY;
		}

		if (Files.exists(path)) {
			try {
				ConfigSerializer.loadWithoutNotifying(path, categories);
			} catch (IOException e) {
				LOGGER.error("Failed to load config schema for: {}", path, e);
			}
		}
		return new LoadResult(getChanges(previousValues), pathChanged);
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

	private void setActivePath(@Nullable Path path) {
		if (Objects.equals(activePath, path)) {
			return;
		}
		flushPendingSaveIfNeeded();
		if (removeFileWatcherCallback != null) {
			removeFileWatcherCallback.run();
			removeFileWatcherCallback = null;
		}
		activePath = path;
		if (path != null && fileWatcher != null) {
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
		if (ConfigSerializer.canLocalizeComments()) {
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

	private void save(Path path) {
		try {
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
		ErrorUtil.checkNotNull(updateBatch, "updateBatch");
		ConfigBatchUpdater updater = new ConfigBatchUpdater();
		try {
			updateBatch.accept(updater);
		} finally {
			updater.close();
		}
		return applyBatchUpdates(updater.getUpdates());
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

		List<AppliedConfigValueChange<?>> changes = new ArrayList<>();
		for (ConfigValueUpdate<?> update : updates) {
			AppliedConfigValueChange<?> change = update.apply();
			if (change != null) {
				changes.add(change);
			}
		}
		if (changes.isEmpty()) {
			return List.of();
		}

		List<AppliedConfigValueChange<?>> immutableChanges = ConfigValue.notifyChangedValues(changes);
		notifyListeners(immutableChanges);
		markDirty();
		return immutableChanges;
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
			update.validate();
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
		if (listeners != null && !changes.isEmpty()) {
			List<IConfigValueBatchChangeListener> listeners = List.copyOf(this.listeners);
			listeners.forEach(listener -> listener.onChange(changes));
		}
	}

	@Override
	public void clearListeners() {
		this.listeners = null;
		for (ConfigCategory configCategory : categories) {
			configCategory.clearListeners();
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
	public Optional<Path> getPath() {
		loadIfNeeded();
		return Optional.ofNullable(activePath);
	}

	private record LoadResult(
		List<AppliedConfigValueChange<?>> changes,
		boolean activePathChanged
	) {
		private static final LoadResult EMPTY = new LoadResult(List.of(), false);
	}
}
