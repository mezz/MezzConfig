package net.mezzdev.config.value;

import net.mezzdev.config.util.ErrorUtil;

import java.util.List;
import java.util.function.Function;

public final class ConfigValueMigration<T> {
	private final Function<String, List<String>> migration;

	private ConfigValueMigration(Function<String, List<String>> migration) {
		this.migration = ErrorUtil.checkNotNull(migration, "migration");
	}

	public static <T> ConfigValueMigration<T> deserialize(ConfigValue<T> configValue) {
		ErrorUtil.checkNotNull(configValue, "configValue");
		return new ConfigValueMigration<>(configValue::setFromSerializedValue);
	}

	public static <T> ConfigValueMigration<T> migrate(ConfigValue<T> configValue, Function<String, T> migration) {
		ErrorUtil.checkNotNull(configValue, "configValue");
		ErrorUtil.checkNotNull(migration, "migration");
		return new ConfigValueMigration<>(value -> migrateValue(configValue, migration, value));
	}

	public List<String> migrate(String value) {
		return migration.apply(value);
	}

	private static <T> List<String> migrateValue(ConfigValue<T> configValue, Function<String, T> migration, String value) {
		try {
			T migratedValue = ErrorUtil.checkNotNull(migration.apply(value), "migratedValue");
			if (!configValue.getSerializer().isValid(migratedValue)) {
				String errorMessage = "Migrated value is invalid. Must be: " + configValue.getSerializer().getValidValuesDescription();
				return List.of(errorMessage);
			}
			configValue.set(migratedValue);
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
