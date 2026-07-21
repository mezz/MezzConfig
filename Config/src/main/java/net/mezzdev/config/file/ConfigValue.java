package net.mezzdev.config.file;

import net.mezzdev.config.ConfigValueUpdateType;
import net.mezzdev.config.IConfigValue;
import net.mezzdev.config.IConfigValueSerializer;
import net.minecraft.network.chat.Component;
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
	private final Component localizedName;
	private final Component description;
	private final T defaultValue;
	private final IConfigValueSerializer<T> serializer;
	private final ConfigValueUpdateType updateType;
	private @Nullable List<Consumer<T>> listeners;
	private volatile T currentValue;
	@Nullable
	private IConfigSchema schema;

	public ConfigValue(
		String localizationPath,
		String name,
		T defaultValue,
		IConfigValueSerializer<T> serializer,
		ConfigValueUpdateType updateType
	) {
		this.name = name;

		this.localizationKey = localizationPath + "." + name;
		String descriptionKey = localizationKey + ".description";
		this.localizedName = Component.translatable(localizationKey);
		this.description = Component.translatable(descriptionKey);
		this.defaultValue = defaultValue;
		this.currentValue = defaultValue;
		this.serializer = serializer;
		this.updateType = updateType;
	}

	public void setSchema(IConfigSchema schema) {
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
	public Component getLocalizedDescription() {
		return description;
	}

	@Override
	public Component getLocalizedName() {
		return localizedName;
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

	@Override
	public ConfigValueUpdateType getUpdateType() {
		return updateType;
	}

	public List<String> setFromSerializedValue(String value) {
		IConfigValueSerializer.IDeserializeResult<T> deserializeResult = serializer.deserialize(value);
		deserializeResult.getResult()
			.ifPresent(t -> {
				if (currentValue != t) {
					currentValue = t;
					if (listeners != null) {
						listeners.forEach(listener -> listener.accept(currentValue));
					}
				}
			});
		return deserializeResult.getErrors();
	}

	@Override
	public boolean set(T value) {
		if (setWithoutNotifying(value)) {
			notifyListeners();
			markDirty();
			return true;
		}
		return false;
	}

	boolean setWithoutNotifying(T value) {
		if (!serializer.isValid(value)) {
			LOGGER.error("Tried to set invalid value : {}\n{}", value,  serializer.getValidValuesDescription());
			return false;
		}
		if (!currentValue.equals(value)) {
			currentValue = value;
			return true;
		}
		return false;
	}

	void notifyListeners() {
		if (listeners != null) {
			listeners.forEach(listener -> listener.accept(currentValue));
		}
	}

	void markDirty() {
		if (schema != null) {
			schema.markDirty();
		}
	}

	@Override
	public void addListener(Consumer<T> listener) {
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
