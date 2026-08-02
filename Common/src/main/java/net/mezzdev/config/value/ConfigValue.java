package net.mezzdev.config.value;

import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueChangeListener;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.util.ConfigNameUtil;
import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.config.schema.ConfigSchema;
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

	public List<String> setFromSerializedValue(String value) {
		IDeserializeResult<T> deserializeResult = serializer.deserialize(value);
		deserializeResult.getResult()
			.ifPresent(t -> {
				if (!currentValue.equals(t)) {
					T oldValue = currentValue;
					currentValue = t;
					notifyListeners(oldValue, currentValue);
				}
			});
		return deserializeResult.getErrors();
	}

	@Override
	public boolean set(T value) {
		T oldValue = currentValue;
		if (setWithoutNotifying(value)) {
			notifyListeners(oldValue, currentValue);
			markDirty();
			return true;
		}
		return false;
	}

	boolean setWithoutNotifying(T value) {
		value = ErrorUtil.checkNotNull(value, "value");
		if (!serializer.isValid(value)) {
			LOGGER.error("Tried to set invalid value : {}\n{}", value, serializer.getValidValuesDescription());
			return false;
		}
		if (!currentValue.equals(value)) {
			currentValue = value;
			return true;
		}
		return false;
	}

	void notifyListeners(T oldValue, T newValue) {
		// TODO: support batched config updates so listeners can observe the final state across multiple changed values.
		if (listeners != null) {
			listeners.forEach(listener -> listener.onChange(oldValue, newValue));
		}
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

	public void clearListeners() {
		if (this.listeners != null) {
			this.listeners = null;
		}
	}
}
