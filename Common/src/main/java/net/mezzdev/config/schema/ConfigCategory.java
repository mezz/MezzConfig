package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.IConfigCategory;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.value.ConfigValueMigration;
import net.mezzdev.config.value.ConfigValueReference;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ConfigCategory implements IConfigCategory {
	private final String name;
	private final String localizationKey;
	private final Map<String, ConfigValue<?>> valueMap;
	private final Map<ConfigValueReference, List<ConfigValueMigration<?>>> movedValueMigrations;

	public ConfigCategory(String localizationPath, String name, List<ConfigValue<?>> values) {
		this(localizationPath, name, values, Map.of());
	}

	public ConfigCategory(
		String localizationPath,
		String name,
		List<ConfigValue<?>> values,
		Map<ConfigValueReference, List<ConfigValueMigration<?>>> movedValueMigrations
	) {
		this.name = name;
		this.localizationKey = localizationPath;
		Map<String, ConfigValue<?>> map = new LinkedHashMap<>();
		for (ConfigValue<?> value : values) {
			ConfigValue<?> previous = map.put(value.getName(), value);
			if (previous != null) {
				throw new IllegalArgumentException("There is already a config value named: " + value.getName());
			}
		}
		this.valueMap = Collections.unmodifiableMap(map);
		this.movedValueMigrations = copyMovedValueMigrations(movedValueMigrations);
	}

	private static Map<ConfigValueReference, List<ConfigValueMigration<?>>> copyMovedValueMigrations(
		Map<ConfigValueReference, List<ConfigValueMigration<?>>> movedValueMigrations
	) {
		Map<ConfigValueReference, List<ConfigValueMigration<?>>> copy = new LinkedHashMap<>();
		movedValueMigrations.forEach((key, value) -> copy.put(key, List.copyOf(value)));
		return Map.copyOf(copy);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getLocalizationKey() {
		return localizationKey;
	}

	public Optional<ConfigValue<?>> getConfigValue(String configValueName) {
		ConfigValue<?> configValue = valueMap.get(configValueName);
		return Optional.ofNullable(configValue);
	}

	@Override
	public Collection<ConfigValue<?>> getConfigValues() {
		return this.valueMap.values();
	}

	public Set<String> getValueNames() {
		return this.valueMap.keySet();
	}

	public List<ConfigValueMigration<?>> getMovedValueMigrations(ConfigValueReference reference) {
		List<ConfigValueMigration<?>> migrations = movedValueMigrations.get(reference);
		if (migrations == null) {
			return List.of();
		}
		return migrations;
	}

	public boolean hasMovedValuesFromCategory(String categoryName) {
		return movedValueMigrations.keySet()
			.stream()
			.anyMatch(reference -> reference.categoryName().equals(categoryName));
	}

	public void clearListeners() {
		for (ConfigValue<?> configValue : this.valueMap.values()) {
			configValue.clearListeners();
		}
	}
}
