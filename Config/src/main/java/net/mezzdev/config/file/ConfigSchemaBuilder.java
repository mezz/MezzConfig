package net.mezzdev.config.file;

import net.mezzdev.config.schema.IConfigDisplayCategoryBuilder;
import net.mezzdev.config.schema.IConfigSchemaBuilder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigSchemaBuilder implements IConfigSchemaBuilder {
	private final Set<String> categoryNames = new HashSet<>();
	private final Set<String> displayCategoryNames = new HashSet<>();
	private final List<ConfigCategoryBuilder> categoryBuilders = new ArrayList<>();
	private final List<ConfigDisplayCategoryBuilder> displayCategoryBuilders = new ArrayList<>();
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
	public ConfigSchema build() {
		if (built) {
			throw new IllegalStateException("Config schema has already been built: " + configFile);
		}
		built = true;
		List<ConfigDisplayCategory> displayCategories = displayCategoryBuilders.stream()
			.map(ConfigDisplayCategoryBuilder::build)
			.toList();
		ConfigSchema schema = new ConfigSchema(configFile, localizationPath, categoryBuilders, displayCategories, configManager.getSaveExecutor());
		configManager.registerSchema(schema);
		return schema;
	}
}
