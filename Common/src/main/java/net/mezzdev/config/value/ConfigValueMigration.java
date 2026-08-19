package net.mezzdev.config.value;

import com.google.gson.JsonElement;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.file.ConfigFileValueAdapter;
import net.mezzdev.config.util.ErrorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class ConfigValueMigration<T> {
	private final ConfigValue<T> configValue;
	private final BiFunction<JsonElement, List<AppliedConfigValueChange<?>>, List<String>> migration;

	private ConfigValueMigration(
		ConfigValue<T> configValue,
		BiFunction<JsonElement, List<AppliedConfigValueChange<?>>, List<String>> migration
	) {
		this.configValue = ErrorUtil.checkNotNull(configValue, "configValue");
		this.migration = ErrorUtil.checkNotNull(migration, "migration");
	}

	public static <T> ConfigValueMigration<T> deserialize(ConfigValue<T> configValue) {
		ErrorUtil.checkNotNull(configValue, "configValue");
		return new ConfigValueMigration<>(configValue, (value, changes) -> configValue.setFromDeserializedValue(
			ConfigFileValueAdapter.deserialize(configValue.getSerializer(), value),
			changes
		));
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
			(value, changes) -> migrateValue(configValue, legacySerializer, migration, value, changes)
		);
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
		ErrorUtil.checkNotNull(changes, "changes");
		return migration.apply(value, changes);
	}

	private static <T, U> List<String> migrateValue(
		ConfigValue<T> configValue,
		IConfigValueSerializer<U> legacySerializer,
		Function<U, T> migration,
		JsonElement value,
		List<AppliedConfigValueChange<?>> changes
	) {
		IDeserializeResult<U> legacyResult = ConfigFileValueAdapter.deserialize(legacySerializer, value);
		List<String> diagnostics = new ArrayList<>(legacyResult.getDiagnostics());
		U legacyValue = legacyResult.getResult().orElse(null);
		if (legacyValue == null) {
			return List.copyOf(diagnostics);
		}
		try {
			T migratedValue = ErrorUtil.checkNotNull(migration.apply(legacyValue), "migratedValue");
			if (!configValue.getSerializer().isValid(migratedValue)) {
				String errorMessage = "Migrated value is invalid. Must be: " + configValue.getSerializer().getValidValuesDescription();
				diagnostics.add(errorMessage);
				return List.copyOf(diagnostics);
			}
			AppliedConfigValueChange<T> change = configValue.setWithoutNotifying(migratedValue);
			if (change != null) {
				changes.add(change);
				configValue.markDirty();
			}
			return List.copyOf(diagnostics);
		} catch (RuntimeException e) {
			String errorMessage = e.getMessage();
			if (errorMessage == null) {
				errorMessage = e.getClass().getName();
			}
			diagnostics.add("Unable to migrate value: " + errorMessage);
			return List.copyOf(diagnostics);
		}
	}
}
