package net.mezzdev.config.schema;

import net.mezzdev.config.api.migration.IConfigMigrationContext;
import net.mezzdev.config.api.sorting.ISortingConfig;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.file.ConfigSerializer;
import net.mezzdev.config.sorting.SortingConfig;
import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.value.ConfigValueUpdate;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ConfigMigrationContext implements IConfigMigrationContext {
	private static final int MAX_DIAGNOSTICS = 100;
	private static final int MAX_DIAGNOSTIC_CHARACTERS = 4 * 1024;
	private final ConfigSchema schema;
	private final Map<ConfigValue<?>, ConfigValueUpdate<?>> valueUpdates = new LinkedHashMap<>();
	private final Map<SortingConfig<?>, SortingConfig.MigrationUpdate<?>> sortingUpdates = new LinkedHashMap<>();
	private final List<String> diagnostics = new ArrayList<>();
	private int rejectedValueCount;
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
	public ConfigMigrationContext rejectValue(String diagnostic) {
		checkOpen();
		diagnostic = ErrorUtil.checkNotNull(diagnostic, "diagnostic");
		if (diagnostic.isBlank()) {
			throw new IllegalArgumentException("diagnostic must not be blank.");
		}
		rejectedValueCount++;
		addDiagnostic(diagnostic);
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

	void addParseResult(ConfigSerializer.MigrationParseResult parseResult) {
		addValueUpdates(parseResult.updates());
		rejectedValueCount += parseResult.rejectedValueCount();
		parseResult.diagnostics().forEach(this::addDiagnostic);
	}

	private void addDiagnostic(String diagnostic) {
		if (diagnostics.size() < MAX_DIAGNOSTICS) {
			if (diagnostic.length() > MAX_DIAGNOSTIC_CHARACTERS) {
				diagnostic = diagnostic.substring(0, MAX_DIAGNOSTIC_CHARACTERS - 1) + "…";
			}
			diagnostics.add(diagnostic);
		} else if (diagnostics.size() == MAX_DIAGNOSTICS) {
			diagnostics.add("Further migration diagnostics were suppressed.");
		}
	}

	int getRejectedValueCount() {
		return rejectedValueCount;
	}

	List<String> getDiagnostics() {
		return List.copyOf(diagnostics);
	}

	boolean hasUpdates() {
		return !valueUpdates.isEmpty() || !sortingUpdates.isEmpty();
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
