package net.mezzdev.config.schema;

import net.mezzdev.config.api.migration.IConfigMigrationContext;
import net.mezzdev.config.api.sorting.ISortingConfig;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.sorting.SortingConfig;
import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.value.ConfigValueUpdate;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ConfigMigrationContext implements IConfigMigrationContext {
	private final ConfigSchema schema;
	private final Map<ConfigValue<?>, ConfigValueUpdate<?>> valueUpdates = new LinkedHashMap<>();
	private final Map<SortingConfig<?>, SortingConfig.MigrationUpdate<?>> sortingUpdates = new LinkedHashMap<>();
	private boolean closed;

	ConfigMigrationContext(ConfigSchema schema) {
		this.schema = schema;
	}

	@Override
	public <T> ConfigMigrationContext set(IConfigValue<T> configValue, T value) {
		checkOpen();
		ErrorUtil.checkNotNull(configValue, "configValue");
		ErrorUtil.checkNotNull(value, "value");
		if (!(configValue instanceof ConfigValue<?> valueImpl)) {
			throw new IllegalArgumentException("Config value was not created by MezzConfig.");
		}
		@SuppressWarnings("unchecked")
		ConfigValue<T> typedValue = (ConfigValue<T>) valueImpl;
		if (!schema.containsConfigValue(typedValue)) {
			throw new IllegalArgumentException("Config value does not belong to the schema being migrated: " + typedValue.getName());
		}
		valueUpdates.put(typedValue, new ConfigValueUpdate<>(typedValue, value));
		return this;
	}

	@Override
	public <T> ConfigMigrationContext setSortedValues(
		ISortingConfig<T> sortingConfig,
		Collection<T> allValues,
		List<T> sortedValues
	) {
		checkOpen();
		ErrorUtil.checkNotNull(sortingConfig, "sortingConfig");
		if (!(sortingConfig instanceof SortingConfig<?> sortingConfigImpl)) {
			throw new IllegalArgumentException("Sorting config was not created by MezzConfig.");
		}
		@SuppressWarnings("unchecked")
		SortingConfig<T> typedSortingConfig = (SortingConfig<T>) sortingConfigImpl;
		SortingConfig.MigrationUpdate<T> update = typedSortingConfig.prepareMigrationUpdate(allValues, sortedValues);
		sortingUpdates.put(typedSortingConfig, update);
		return this;
	}

	List<ConfigValueUpdate<?>> getValueUpdates() {
		return List.copyOf(valueUpdates.values());
	}

	void addValueUpdates(List<ConfigValueUpdate<?>> updates) {
		checkOpen();
		for (ConfigValueUpdate<?> update : updates) {
			if (!schema.containsConfigValue(update.configValue())) {
				throw new IllegalArgumentException(
					"Config value does not belong to the schema being migrated: " + update.configValue().getName()
				);
			}
			valueUpdates.put(update.configValue(), update);
		}
	}

	List<SortingConfig.MigrationUpdate<?>> getSortingUpdates() {
		return List.copyOf(sortingUpdates.values());
	}

	void close() {
		closed = true;
	}

	private void checkOpen() {
		if (closed) {
			throw new IllegalStateException("Config migration context has already closed.");
		}
	}
}
