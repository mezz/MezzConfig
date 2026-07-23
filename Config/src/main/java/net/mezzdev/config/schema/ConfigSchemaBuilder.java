package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.IConfigDisplayCategoryBuilder;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.value.ConfigValueMigration;
import net.mezzdev.config.value.ConfigValueReference;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ConfigSchemaBuilder implements IConfigSchemaBuilder {
	private final Set<String> categoryNames = new HashSet<>();
	private final Set<String> displayCategoryNames = new HashSet<>();
	private final List<ConfigCategoryBuilder> categoryBuilders = new ArrayList<>();
	private final List<ConfigDisplayCategoryBuilder> displayCategoryBuilders = new ArrayList<>();
	private final Map<ConfigValueReference, List<ConfigValueMigration<?, ?>>> legacyValueMigrations = new LinkedHashMap<>();
	private final Path configFile;
	private final String localizationPath;
	private final ConfigManager configManager;
	private boolean built;

	public ConfigSchemaBuilder(Path configFile, String localizationPath, ConfigManager configManager) {
		this.configFile = configFile;
		this.localizationPath = localizationPath;
		this.configManager = configManager;
	}

	@Override
	public ConfigCategoryBuilder addCategory(String name) {
		if (!categoryNames.add(name)) {
			throw new IllegalArgumentException("There is already a category named: " + name);
		}
		ConfigCategoryBuilder category = new ConfigCategoryBuilder(localizationPath, name);
		this.categoryBuilders.add(category);
		return category;
	}

	@Override
	public IConfigDisplayCategoryBuilder addDisplayCategory(String name) {
		if (!displayCategoryNames.add(name)) {
			throw new IllegalArgumentException("There is already a display category named: " + name);
		}
		ConfigDisplayCategoryBuilder displayCategory = new ConfigDisplayCategoryBuilder(localizationPath + "." + name, name);
		this.displayCategoryBuilders.add(displayCategory);
		return displayCategory;
	}

	@Override
	public <T, R> void addLegacyValueMigration(
		String legacyCategoryName,
		String legacyValueName,
		IConfigValueSerializer<T> legacySerializer, Function<T, R> migration, IConfigValue<R> newConfigValue
	) {
		ConfigValueReference reference = new ConfigValueReference(legacyCategoryName, legacyValueName);
		ConfigValueMigration<T, R> valueMigration = new ConfigValueMigration<>(newConfigValue, legacySerializer, migration);
		legacyValueMigrations.computeIfAbsent(reference, key -> new ArrayList<>())
			.add(valueMigration);
	}

	@Override
	public ConfigSchema build() {
		if (built) {
			throw new IllegalStateException("Config schema has already been built: " + configFile);
		}
		built = true;
		List<ConfigDisplayCategory> displayCategories = displayCategoryBuilders.stream()
			.map(ConfigDisplayCategoryBuilder::build)
			.toList();
		ConfigSchema schema = new ConfigSchema(
			configFile,
			localizationPath,
			categoryBuilders,
			displayCategories,
			legacyValueMigrations,
			configManager.getSaveScheduler()
		);
		configManager.registerSchema(schema);
		return schema;
	}
}
