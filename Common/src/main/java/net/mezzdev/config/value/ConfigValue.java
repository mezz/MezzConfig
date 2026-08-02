package net.mezzdev.config.value;

import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueBatchChangeListener;
import net.mezzdev.config.api.value.IConfigValueChangeListener;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.util.ConfigNameUtil;
import net.mezzdev.config.util.ErrorUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConfigValue<T> implements IConfigValue<T>, Supplier<T> {
	private static final Logger LOGGER = LogManager.getLogger();

	private final String name;
	private final String localizationKey;
	private final T defaultValue;
	private final IConfigValueSerializer<T> serializer;
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
		this.name = ConfigNameUtil.validateConfigName(name, "configValueName");

		localizationPath = ErrorUtil.checkNotNull(localizationPath, "localizationPath");
		this.localizationKey = localizationPath + "." + this.name;
		this.defaultValue = ErrorUtil.checkNotNull(defaultValue, "defaultValue");
		this.serializer = ErrorUtil.checkNotNull(serializer, "serializer");
		if (!this.serializer.isValid(this.defaultValue)) {
			throw new IllegalArgumentException("Default value for '%s' is invalid: %s".formatted(this.name, this.defaultValue));
		}
		this.currentValue = this.defaultValue;
	}

	public void setSchema(ConfigSchema schema) {
		this.schema = schema;
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
			listeners.forEach(listener -> listener.onChange(change.oldValue(), change.newValue()));
		}
		if (batchListeners != null) {
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
	public void addListener(Consumer<T> listener) {
		addListener((oldValue, newValue) -> listener.accept(newValue));
	}

	@Override
	public void addListener(IConfigValueChangeListener<T> listener) {
		if (this.listeners == null) {
			this.listeners = new ArrayList<>();
		}
		this.listeners.add(listener);
	}

	@Override
	public void addBatchListener(IConfigValueBatchChangeListener listener) {
		if (this.batchListeners == null) {
			this.batchListeners = new ArrayList<>();
		}
		this.batchListeners.add(listener);
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
