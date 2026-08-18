package net.mezzdev.config.value;

import net.mezzdev.config.util.ErrorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class ConfigValueMigration<T> {
	private final ConfigValue<T> configValue;
	private final BiFunction<String, List<AppliedConfigValueChange<?>>, List<String>> migration;

	private ConfigValueMigration(
		ConfigValue<T> configValue,
		BiFunction<String, List<AppliedConfigValueChange<?>>, List<String>> migration
	) {
		this.configValue = ErrorUtil.checkNotNull(configValue, "configValue");
		this.migration = ErrorUtil.checkNotNull(migration, "migration");
	}

	public static <T> ConfigValueMigration<T> deserialize(ConfigValue<T> configValue) {
		ErrorUtil.checkNotNull(configValue, "configValue");
		return new ConfigValueMigration<>(configValue, configValue::setFromSerializedValue);
	}

	public static <T> ConfigValueMigration<T> migrate(ConfigValue<T> configValue, Function<String, T> migration) {
		ErrorUtil.checkNotNull(configValue, "configValue");
		ErrorUtil.checkNotNull(migration, "migration");
		return new ConfigValueMigration<>(configValue, (value, changes) -> migrateValue(configValue, migration, value, changes));
	}

	public List<String> migrate(String value) {
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

	public List<String> migrate(String value, List<AppliedConfigValueChange<?>> changes) {
		ErrorUtil.checkNotNull(changes, "changes");
		return migration.apply(value, changes);
	}

	private static <T> List<String> migrateValue(
		ConfigValue<T> configValue,
		Function<String, T> migration,
		String value,
		List<AppliedConfigValueChange<?>> changes
	) {
		try {
			T migratedValue = ErrorUtil.checkNotNull(migration.apply(value), "migratedValue");
			if (!configValue.getSerializer().isValid(migratedValue)) {
				String errorMessage = "Migrated value is invalid. Must be: " + configValue.getSerializer().getValidValuesDescription();
				return List.of(errorMessage);
			}
			AppliedConfigValueChange<T> change = configValue.setWithoutNotifying(migratedValue);
			if (change != null) {
				changes.add(change);
				configValue.markDirty();
			}
			return List.of();
		} catch (RuntimeException e) {
			String errorMessage = e.getMessage();
			if (errorMessage == null) {
				errorMessage = e.getClass().getName();
			}
			return List.of("Unable to migrate value: " + errorMessage);
		}
	}
}
