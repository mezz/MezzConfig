package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.update.IConfigBatchUpdater;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.value.ConfigValueUpdate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigBatchUpdater implements IConfigBatchUpdater {
	private final Map<ConfigValue<?>, ConfigValueUpdate<?>> updates = new LinkedHashMap<>();
	private boolean closed;

	@Override
	public <T> ConfigBatchUpdater set(IConfigValue<T> configValue, T value) {
		checkOpen();
		ErrorUtil.checkNotNull(configValue, "configValue");
		ErrorUtil.checkNotNull(value, "value");
		if (configValue instanceof ConfigValue<?> valueImpl) {
			@SuppressWarnings("unchecked")
			ConfigValue<T> typedValue = (ConfigValue<T>) valueImpl;
			updates.put(typedValue, new ConfigValueUpdate<>(typedValue, value));
			return this;
		}
		throw new IllegalArgumentException("Config value was not created by MezzConfig.");
	}

	List<ConfigValueUpdate<?>> getUpdates() {
		return updates.values()
			.stream()
			.toList();
	}

	void close() {
		closed = true;
	}

	private void checkOpen() {
		if (closed) {
			throw new IllegalStateException("Config batch updater has already closed.");
		}
	}
}
