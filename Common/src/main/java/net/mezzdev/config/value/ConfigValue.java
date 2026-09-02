package net.mezzdev.config.value;

import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.api.value.ConfigValueEditMode;
import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.mezzdev.config.api.value.IConfigListValueSerializer;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueBatchChangeListener;
import net.mezzdev.config.api.value.IConfigValueChangeListener;
import net.mezzdev.config.api.value.IConfigValueEditorInfo;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.schema.IConfigEditorCategory;
import net.mezzdev.config.file.ConfigFileValueAdapter;
import net.mezzdev.config.schema.ConfigEditorCategory;
import net.mezzdev.config.schema.ConfigEditorCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.util.ConfigNameUtil;
import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.config.util.ListenerList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ConfigValue<T> implements IConfigValue<T>, IConfigValueEditorInfo<T>, Supplier<T> {
	private static final Logger LOGGER = LogManager.getLogger();

	private final String name;
	private final String localizationKey;
	private final T defaultValue;
	private final IConfigValueSerializer<T> serializer;
	private final ConfigValueEditMode editMode;
	private final ConfigValueRestartRequirement restartRequirement;
	private final List<ConfigEditorCategoryBuilder> editorCategoryBuilders;
	private List<ConfigEditorCategory> editorCategories = List.of();
	private final ListenerList<IConfigValueChangeListener<T>> listeners = new ListenerList<>();
	private final ListenerList<IConfigValueBatchChangeListener> batchListeners = new ListenerList<>();
	private final ListenerList<IConfigValueChangeListener<T>> pendingListeners = new ListenerList<>();
	private final ListenerList<IConfigValueBatchChangeListener> pendingBatchListeners = new ListenerList<>();
	private volatile T effectiveValue;
	private volatile T pendingValue;
	@Nullable
	private ConfigSchema schema;

	public ConfigValue(
		String localizationPath,
		String name,
		T defaultValue,
		IConfigValueSerializer<T> serializer
	) {
		this(localizationPath, name, defaultValue, serializer, ConfigValueEditMode.BATCH, ConfigValueRestartRequirement.NONE, List.of());
	}

	public ConfigValue(
		String localizationPath,
		String name,
		T defaultValue,
		IConfigValueSerializer<T> serializer,
		ConfigValueEditMode editMode,
		Iterable<ConfigEditorCategoryBuilder> editorCategoryBuilders
	) {
		this(localizationPath, name, defaultValue, serializer, editMode, ConfigValueRestartRequirement.NONE, editorCategoryBuilders);
	}

	public ConfigValue(
		String localizationPath,
		String name,
		T defaultValue,
		IConfigValueSerializer<T> serializer,
		ConfigValueEditMode editMode,
		ConfigValueRestartRequirement restartRequirement,
		Iterable<ConfigEditorCategoryBuilder> editorCategoryBuilders
	) {
		this.name = ConfigNameUtil.validateConfigName(name, "configValueName");

		localizationPath = ErrorUtil.checkNotNull(localizationPath, "localizationPath");
		this.localizationKey = localizationPath + "." + this.name;
		this.serializer = ErrorUtil.checkNotNull(serializer, "serializer");
		defaultValue = ErrorUtil.checkNotNull(defaultValue, "defaultValue");
		this.editMode = ErrorUtil.checkNotNull(editMode, "editMode");
		this.restartRequirement = ErrorUtil.checkNotNull(restartRequirement, "restartRequirement");
		this.editorCategoryBuilders = getEditorCategoryBuilders(editorCategoryBuilders);
		if (!isValid(this.serializer, defaultValue)) {
			throw new IllegalArgumentException("Default value for '%s' is invalid: %s".formatted(this.name, defaultValue));
		}
		this.defaultValue = snapshotValue(this.serializer, defaultValue);
		validateSerializerRoundTrip(this.serializer, this.defaultValue, this.name);
		this.effectiveValue = this.defaultValue;
		this.pendingValue = this.defaultValue;
	}

	@SuppressWarnings("unchecked")
	static <T> T snapshotValue(IConfigValueSerializer<T> serializer, T value) {
		if (serializer instanceof IConfigListValueSerializer<?>) {
			if (!(value instanceof List<?> list)) {
				throw new IllegalArgumentException("List config serializers require list values.");
			}
			try {
				return (T) List.copyOf(list);
			} catch (NullPointerException e) {
				throw new IllegalArgumentException("List config values must not contain null elements.", e);
			}
		}
		return value;
	}

	private static List<ConfigEditorCategoryBuilder> getEditorCategoryBuilders(Iterable<ConfigEditorCategoryBuilder> editorCategoryBuilders) {
		ErrorUtil.checkNotNull(editorCategoryBuilders, "editorCategoryBuilders");
		Set<ConfigEditorCategoryBuilder> categoryBuilders = new LinkedHashSet<>();
		for (ConfigEditorCategoryBuilder categoryBuilder : editorCategoryBuilders) {
			categoryBuilder = ErrorUtil.checkNotNull(categoryBuilder, "editorCategoryBuilder");
			if (!categoryBuilders.add(categoryBuilder)) {
				throw new IllegalArgumentException("There is already an editor category: " + categoryBuilder.getName());
			}
		}
		return List.copyOf(categoryBuilders);
	}

	public void setSchema(ConfigSchema schema) {
		this.schema = schema;
	}

	public void resolveEditorCategories(
		List<ConfigEditorCategoryBuilder> categoryBuilders,
		Map<ConfigEditorCategoryBuilder, ? extends ConfigEditorCategory> categories
	) {
		ErrorUtil.checkNotNull(categoryBuilders, "categoryBuilders");
		ErrorUtil.checkNotNull(categories, "categories");
		Set<ConfigEditorCategoryBuilder> unresolvedCategoryBuilders = new LinkedHashSet<>(editorCategoryBuilders);
		List<ConfigEditorCategory> resolvedCategories = new ArrayList<>();
		for (ConfigEditorCategoryBuilder categoryBuilder : categoryBuilders) {
			if (unresolvedCategoryBuilders.remove(categoryBuilder)) {
				ConfigEditorCategory category = categories.get(categoryBuilder);
				if (category == null) {
					throw new IllegalStateException("Editor category has not been built: " + categoryBuilder.getName());
				}
				resolvedCategories.add(category);
			}
		}
		if (!unresolvedCategoryBuilders.isEmpty()) {
			String categoryNames = String.join(", ", unresolvedCategoryBuilders.stream()
				.map(ConfigEditorCategoryBuilder::getName)
				.toList());
			throw new IllegalStateException("Editor categories have not been built: " + categoryNames);
		}
		this.editorCategories = List.copyOf(resolvedCategories);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getLocalizationKey() {
		return localizationKey;
	}

	@Override
	public T getDefaultValue() {
		return defaultValue;
	}

	@Override
	public IConfigValueEditorInfo<T> getEditorInfo() {
		return this;
	}

	@Override
	public ConfigValueEditMode getEditMode() {
		return editMode;
	}

	@Override
	public ConfigValueRestartRequirement getRestartRequirement() {
		return restartRequirement;
	}

	@Override
	public List<? extends IConfigEditorCategory> getEditorCategories() {
		return editorCategories;
	}

	@Override
	public T get() {
		if (schema != null) {
			return schema.getEffectiveValue(this);
		}
		return getEffectiveValueWithoutLoading();
	}

	@Override
	public T getPendingValue() {
		if (schema != null) {
			return schema.getPendingValue(this);
		}
		return getPendingValueWithoutLoading();
	}

	public T getEffectiveValueWithoutLoading() {
		return effectiveValue;
	}

	public T getPendingValueWithoutLoading() {
		return pendingValue;
	}

	@Override
	public IConfigValueSerializer<T> getSerializer() {
		return serializer;
	}

	public List<String> setFromDeserializedValue(
		IDeserializeResult<T> deserializeResult,
		List<AppliedConfigValueChange<?>> changes
	) {
		ErrorUtil.checkNotNull(deserializeResult, "deserializeResult");
		ErrorUtil.checkNotNull(changes, "changes");
		deserializeResult.getResult()
			.ifPresent(t -> {
				AppliedConfigValueChange<T> change = setWithoutNotifying(t);
				if (change != null) {
					changes.add(change);
				}
			});
		return deserializeResult.getDiagnostics();
	}

	@Override
	public boolean set(T value) {
		if (schema != null) {
			return !schema.batchUpdate(updater -> updater.set(this, value))
				.isEmpty();
		}
		T previousEffectiveValue = effectiveValue;
		AppliedConfigValueChange<T> change = setWithoutNotifying(value);
		if (change == null) {
			return false;
		}
		markDirty();
		notifyPendingChangedValues(List.of(change));
		if (!previousEffectiveValue.equals(effectiveValue)) {
			notifyChangedValues(List.of(new AppliedConfigValueChange<>(this, previousEffectiveValue, effectiveValue)));
		}
		return true;
	}

	void validateUpdateValue(T value) {
		if (value == null || !isValid(serializer, value)) {
			throw new IllegalArgumentException("Invalid value for '%s': %s\n%s".formatted(name, value, serializer.getValidValuesDescription()));
		}
	}

	T snapshotUpdateValue(T value) {
		validateUpdateValue(value);
		T snapshot = snapshotValue(serializer, value);
		validateSerializerRoundTrip(serializer, snapshot, name);
		return snapshot;
	}

	private static <T> boolean isValid(IConfigValueSerializer<T> serializer, T value) {
		try {
			return serializer.isValid(value);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Config serializer failed to validate a value.", e);
		}
	}

	private static <T> void validateSerializerRoundTrip(
		IConfigValueSerializer<T> serializer,
		T value,
		String configValueName
	) {
		try {
			ConfigFileValueAdapter.validateRoundTrip(serializer, value);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(
				"Serializer for config value '%s' cannot round-trip a valid value: %s".formatted(
					configValueName,
					getExceptionMessage(e)
				),
				e
			);
		}
	}

	private static String getExceptionMessage(RuntimeException exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return exception.getClass().getSimpleName();
		}
		return message;
	}

	@Nullable
	AppliedConfigValueChange<T> setWithoutNotifying(T value) {
		T newValue = snapshotUpdateValue(value);
		return setValidatedValueWithoutNotifying(newValue);
	}

	@Nullable
	AppliedConfigValueChange<T> setValidatedValueWithoutNotifying(T newValue) {
		if (!pendingValue.equals(newValue)) {
			T oldValue = pendingValue;
			pendingValue = newValue;
			if (restartRequirement == ConfigValueRestartRequirement.NONE) {
				effectiveValue = newValue;
			}
			return new AppliedConfigValueChange<>(this, oldValue, pendingValue);
		}
		return null;
	}

	public void resetToDefaultWithoutNotifying() {
		pendingValue = defaultValue;
		if (restartRequirement == ConfigValueRestartRequirement.NONE) {
			effectiveValue = defaultValue;
		}
	}

	public void resetAllToDefaultWithoutNotifying() {
		effectiveValue = defaultValue;
		pendingValue = defaultValue;
	}

	public @Nullable AppliedConfigValueChange<T> promotePendingValueWithoutNotifying() {
		if (!effectiveValue.equals(pendingValue)) {
			T oldValue = effectiveValue;
			effectiveValue = pendingValue;
			return new AppliedConfigValueChange<>(this, oldValue, effectiveValue);
		}
		return null;
	}

	public @Nullable AppliedConfigValueChange<T> setSynchronizedValuesWithoutNotifying(T effectiveValue, T pendingValue) {
		T newEffectiveValue = snapshotUpdateValue(effectiveValue);
		T newPendingValue = snapshotUpdateValue(pendingValue);
		T oldEffectiveValue = this.effectiveValue;
		this.effectiveValue = newEffectiveValue;
		this.pendingValue = newPendingValue;
		if (!oldEffectiveValue.equals(newEffectiveValue)) {
			return new AppliedConfigValueChange<>(this, oldEffectiveValue, newEffectiveValue);
		}
		return null;
	}

	public static List<AppliedConfigValueChange<?>> notifyChangedValues(List<? extends AppliedConfigValueChange<?>> changes) {
		return notifyChangedValues(changes, false);
	}

	public static List<AppliedConfigValueChange<?>> notifyPendingChangedValues(
		List<? extends AppliedConfigValueChange<?>> changes
	) {
		return notifyChangedValues(changes, true);
	}

	private static List<AppliedConfigValueChange<?>> notifyChangedValues(
		List<? extends AppliedConfigValueChange<?>> changes,
		boolean pending
	) {
		if (changes.isEmpty()) {
			return List.of();
		}
		List<AppliedConfigValueChange<?>> immutableChanges = List.copyOf(changes);
		BatchListenerTracker batchListenerTracker = new BatchListenerTracker();
		List<Runnable> notifications = immutableChanges.stream()
			.map(change -> change.configValue().snapshotNotifications(immutableChanges, pending, batchListenerTracker))
			.toList();
		notifications.forEach(Runnable::run);
		return immutableChanges;
	}

	private Runnable snapshotNotifications(
		List<? extends AppliedConfigValueChange<?>> changes,
		boolean pending,
		BatchListenerTracker batchListenerTracker
	) {
		AppliedConfigValueChange<T> change = getChange(changes);
		List<IConfigValueChangeListener<T>> listenerSnapshot;
		List<IConfigValueBatchChangeListener> batchListenerSnapshot;
		String listenerDescription;
		String batchListenerDescription;
		if (pending) {
			listenerSnapshot = pendingListeners.snapshot();
			batchListenerSnapshot = pendingBatchListeners.snapshot();
			listenerDescription = "Pending config value listener";
			batchListenerDescription = "Pending config value batch listener";
		} else {
			listenerSnapshot = listeners.snapshot();
			batchListenerSnapshot = batchListeners.snapshot();
			listenerDescription = "Config value listener";
			batchListenerDescription = "Config value batch listener";
		}
		return () -> notifyListeners(
			change,
			changes,
			listenerSnapshot,
			batchListenerSnapshot,
			listenerDescription,
			batchListenerDescription,
			batchListenerTracker
		);
	}

	private void notifyListeners(
		AppliedConfigValueChange<T> change,
		List<? extends AppliedConfigValueChange<?>> changes,
		List<IConfigValueChangeListener<T>> listenerSnapshot,
		List<IConfigValueBatchChangeListener> batchListenerSnapshot,
		String listenerDescription,
		String batchListenerDescription,
		BatchListenerTracker batchListenerTracker
	) {
		for (IConfigValueChangeListener<T> listener : listenerSnapshot) {
			try {
				listener.onConfigValueChanged(change);
			} catch (RuntimeException e) {
				LOGGER.error("{} failed for '{}'.", listenerDescription, name, e);
			}
		}
		for (IConfigValueBatchChangeListener listener : batchListenerSnapshot) {
			if (!batchListenerTracker.shouldNotify(this, listener)) {
				continue;
			}
			try {
				listener.onConfigValuesChanged(changes);
			} catch (RuntimeException e) {
				LOGGER.error("{} failed for '{}'.", batchListenerDescription, name, e);
			}
		}
	}

	private static final class BatchListenerTracker {
		private final Map<IConfigValueBatchChangeListener, ConfigValue<?>> firstOwners = new IdentityHashMap<>();

		private boolean shouldNotify(ConfigValue<?> owner, IConfigValueBatchChangeListener listener) {
			ConfigValue<?> firstOwner = firstOwners.get(listener);
			if (firstOwner == null) {
				firstOwners.put(listener, owner);
				return true;
			}
			return firstOwner == owner;
		}
	}

	@SuppressWarnings("unchecked")
	private AppliedConfigValueChange<T> getChange(List<? extends AppliedConfigValueChange<?>> changes) {
		for (AppliedConfigValueChange<?> change : changes) {
			if (change.configValue() == this) {
				return (AppliedConfigValueChange<T>) change;
			}
		}
		throw new IllegalArgumentException("Changes do not contain this config value: " + name);
	}

	void markDirty() {
		if (schema != null) {
			schema.markDirty();
		}
	}

	@Override
	public Runnable addListener(IConfigValueChangeListener<T> listener) {
		ErrorUtil.checkNotNull(listener, "listener");
		return this.listeners.add(listener);
	}

	@Override
	public Runnable addPendingListener(IConfigValueChangeListener<T> listener) {
		ErrorUtil.checkNotNull(listener, "listener");
		return this.pendingListeners.add(listener);
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
}
