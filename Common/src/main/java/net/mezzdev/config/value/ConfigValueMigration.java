package net.mezzdev.config.value;

import com.google.gson.JsonElement;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.file.ConfigFileValueAdapter;
import net.mezzdev.config.util.ErrorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class ConfigValueMigration<T> {
	private final ConfigValue<T> configValue;
	private final Function<JsonElement, IDeserializeResult<T>> migration;

	private ConfigValueMigration(
		ConfigValue<T> configValue,
		Function<JsonElement, IDeserializeResult<T>> migration
	) {
		this.configValue = ErrorUtil.checkNotNull(configValue, "configValue");
		this.migration = ErrorUtil.checkNotNull(migration, "migration");
	}

	public static <T> ConfigValueMigration<T> deserialize(ConfigValue<T> configValue) {
		ErrorUtil.checkNotNull(configValue, "configValue");
		return new ConfigValueMigration<>(
			configValue,
			value -> ConfigFileValueAdapter.deserialize(configValue.getSerializer(), value)
		);
	}

	public static <T, U> ConfigValueMigration<T> migrate(
		ConfigValue<T> configValue,
		IConfigValueSerializer<U> legacySerializer,
		Function<U, T> migration
	) {
		ErrorUtil.checkNotNull(configValue, "configValue");
		ErrorUtil.checkNotNull(legacySerializer, "legacySerializer");
		ErrorUtil.checkNotNull(migration, "migration");
		return new ConfigValueMigration<>(
			configValue,
			value -> migrateValue(configValue, legacySerializer, migration, value)
		);
	}

	public ConfigValue<T> configValue() {
		return configValue;
	}

	public IDeserializeResult<T> deserialize(JsonElement value) {
		return migration.apply(value);
	}

	public List<String> migrate(JsonElement value) {
		T previousEffectiveValue = configValue.getEffectiveValueWithoutLoading();
		List<AppliedConfigValueChange<?>> changes = new ArrayList<>();
		List<String> diagnostics = migrate(value, changes);
		ConfigValue.notifyPendingChangedValues(changes);
		T effectiveValue = configValue.getEffectiveValueWithoutLoading();
		if (!Objects.equals(previousEffectiveValue, effectiveValue)) {
			ConfigValue.notifyChangedValues(List.of(
				new AppliedConfigValueChange<>(configValue, previousEffectiveValue, effectiveValue)
			));
		}
		return diagnostics;
	}

	public List<String> migrate(JsonElement value, List<AppliedConfigValueChange<?>> changes) {
		return apply(deserialize(value), changes);
	}

	public List<String> apply(
		IDeserializeResult<T> result,
		List<AppliedConfigValueChange<?>> changes
	) {
		ErrorUtil.checkNotNull(result, "result");
		ErrorUtil.checkNotNull(changes, "changes");
		int previousChangeCount = changes.size();
		List<String> diagnostics = configValue.setFromDeserializedValue(result, changes);
		if (changes.size() > previousChangeCount) {
			configValue.markDirty();
		}
		return diagnostics;
	}

	private static <T, U> IDeserializeResult<T> migrateValue(
		ConfigValue<T> configValue,
		IConfigValueSerializer<U> legacySerializer,
		Function<U, T> migration,
		JsonElement value
	) {
		IDeserializeResult<U> legacyResult = ConfigFileValueAdapter.deserialize(legacySerializer, value);
		List<String> diagnostics = new ArrayList<>(legacyResult.getDiagnostics());
		U legacyValue = legacyResult.getResult().orElse(null);
		if (legacyValue == null) {
			return IDeserializeResult.failure(diagnostics);
		}
		try {
			T migratedValue = ErrorUtil.checkNotNull(migration.apply(legacyValue), "migratedValue");
			if (!configValue.getSerializer().isValid(migratedValue)) {
				String errorMessage = "Migrated value is invalid. Must be: " + configValue.getSerializer().getValidValuesDescription();
				diagnostics.add(errorMessage);
				return IDeserializeResult.failure(diagnostics);
			}
			migratedValue = configValue.snapshotUpdateValue(migratedValue);
			if (diagnostics.isEmpty()) {
				return IDeserializeResult.success(migratedValue);
			}
			return IDeserializeResult.partialSuccess(migratedValue, diagnostics);
		} catch (RuntimeException e) {
			String errorMessage = e.getMessage();
			if (errorMessage == null) {
				errorMessage = e.getClass().getName();
			}
			diagnostics.add("Unable to migrate value: " + errorMessage);
			return IDeserializeResult.failure(diagnostics);
		}
	}
}
