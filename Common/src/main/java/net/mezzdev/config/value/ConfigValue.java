package net.mezzdev.config.value;

import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.api.value.ConfigValueEditMode;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueBatchChangeListener;
import net.mezzdev.config.api.value.IConfigValueChangeListener;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.schema.IConfigEditorCategory;
import net.mezzdev.config.schema.ConfigEditorCategory;
import net.mezzdev.config.schema.ConfigEditorCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.util.ConfigNameUtil;
import net.mezzdev.config.util.ErrorUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ConfigValue<T> implements IConfigValue<T>, Supplier<T> {
	private static final Logger LOGGER = LogManager.getLogger();

	private final String name;
	private final String localizationKey;
	private final T defaultValue;
	private final IConfigValueSerializer<T> serializer;
	private final ConfigValueEditMode editMode;
	private final List<ConfigEditorCategoryBuilder> editorCategoryBuilders;
	private List<ConfigEditorCategory> editorCategories = List.of();
	private @Nullable List<IConfigValueChangeListener<T>> listeners;
	private @Nullable List<IConfigValueBatchChangeListener> batchListeners;
	private volatile T currentValue;
	@Nullable
	private ConfigSchema schema;

	public ConfigValue(
		String localizationPath,
		String name,
		T defaultValue,
		IConfigValueSerializer<T> serializer
	) {
		this(localizationPath, name, defaultValue, serializer, ConfigValueEditMode.BATCH, List.of());
	}

	public ConfigValue(
		String localizationPath,
		String name,
		T defaultValue,
		IConfigValueSerializer<T> serializer,
		ConfigValueEditMode editMode,
		Iterable<ConfigEditorCategoryBuilder> editorCategoryBuilders
	) {
		this.name = ConfigNameUtil.validateConfigName(name, "configValueName");

		localizationPath = ErrorUtil.checkNotNull(localizationPath, "localizationPath");
		this.localizationKey = localizationPath + "." + this.name;
		this.defaultValue = ErrorUtil.checkNotNull(defaultValue, "defaultValue");
		this.serializer = ErrorUtil.checkNotNull(serializer, "serializer");
		this.editMode = ErrorUtil.checkNotNull(editMode, "editMode");
		this.editorCategoryBuilders = getEditorCategoryBuilders(editorCategoryBuilders);
		if (!this.serializer.isValid(this.defaultValue)) {
			throw new IllegalArgumentException("Default value for '%s' is invalid: %s".formatted(this.name, this.defaultValue));
		}
		this.currentValue = this.defaultValue;
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
	public ConfigValueEditMode getEditMode() {
		return editMode;
	}

	@Override
	public List<? extends IConfigEditorCategory> getEditorCategories() {
		return editorCategories;
	}

	@Override
	public T getValue() {
		if (schema != null) {
			schema.loadIfNeeded();
		}
		return currentValue;
	}

	@Override
	public T get() {
		return getValue();
	}

	@Override
	public IConfigValueSerializer<T> getSerializer() {
		return serializer;
	}

	public List<String> setFromSerializedValue(String value, List<AppliedConfigValueChange<?>> changes) {
		ErrorUtil.checkNotNull(changes, "changes");
		IDeserializeResult<T> deserializeResult = serializer.deserialize(value);
		deserializeResult.getResult()
			.ifPresent(t -> {
				AppliedConfigValueChange<T> change = setWithoutNotifying(t);
				if (change != null) {
					changes.add(change);
				}
			});
		return deserializeResult.getErrors();
	}

	@Override
	public boolean set(T value) {
		if (!canSet(value)) {
			return false;
		}
		if (schema != null) {
			return !schema.batchUpdate(updater -> updater.set(this, value))
				.isEmpty();
		}
		AppliedConfigValueChange<T> change = setWithoutNotifying(value);
		if (change == null) {
			return false;
		}
		notifyListeners(change);
		markDirty();
		return true;
	}

	private boolean canSet(T value) {
		ErrorUtil.checkNotNull(value, "value");
		if (!serializer.isValid(value)) {
			LOGGER.error("Tried to set invalid value : {}\n{}", value, serializer.getValidValuesDescription());
			return false;
		}
		return true;
	}

	void validateUpdateValue(T value) {
		ErrorUtil.checkNotNull(value, "value");
		if (!serializer.isValid(value)) {
			throw new IllegalArgumentException("Invalid value for '%s': %s\n%s".formatted(name, value, serializer.getValidValuesDescription()));
		}
	}

	@Nullable
	AppliedConfigValueChange<T> setWithoutNotifying(T value) {
		validateUpdateValue(value);
		if (!currentValue.equals(value)) {
			T oldValue = currentValue;
			currentValue = value;
			return new AppliedConfigValueChange<>(this, oldValue, currentValue);
		}
		return null;
	}

	void notifyListeners(AppliedConfigValueChange<T> change) {
		notifyChangedValues(List.of(change));
	}

	public static List<AppliedConfigValueChange<?>> notifyChangedValues(List<? extends AppliedConfigValueChange<?>> changes) {
		if (changes.isEmpty()) {
			return List.of();
		}
		List<AppliedConfigValueChange<?>> immutableChanges = List.copyOf(changes);
		for (AppliedConfigValueChange<?> change : immutableChanges) {
			change.configValue()
				.notifyListeners(immutableChanges);
		}
		return immutableChanges;
	}

	public void notifyListeners(List<? extends AppliedConfigValueChange<?>> changes) {
		AppliedConfigValueChange<T> change = getChange(changes);
		if (listeners != null) {
			List<IConfigValueChangeListener<T>> listeners = List.copyOf(this.listeners);
			listeners.forEach(listener -> listener.onChange(change));
		}
		if (batchListeners != null) {
			List<IConfigValueBatchChangeListener> batchListeners = List.copyOf(this.batchListeners);
			batchListeners.forEach(listener -> listener.onChange(changes));
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

	@Override
	public Runnable addBatchListener(IConfigValueBatchChangeListener listener) {
		ErrorUtil.checkNotNull(listener, "listener");
		if (this.batchListeners == null) {
			this.batchListeners = new ArrayList<>();
		}
		this.batchListeners.add(listener);
		return () -> {
			if (this.batchListeners != null) {
				this.batchListeners.remove(listener);
			}
		};
	}

	public void clearListeners() {
		if (this.listeners != null) {
			this.listeners = null;
		}
		if (this.batchListeners != null) {
			this.batchListeners = null;
		}
	}
}
