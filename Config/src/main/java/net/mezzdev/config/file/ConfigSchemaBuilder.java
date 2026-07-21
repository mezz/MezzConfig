package net.mezzdev.config.file;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigSchemaBuilder implements IConfigSchemaBuilder {
	private final Set<String> categoryNames = new HashSet<>();
	private final List<ConfigCategoryBuilder> categoryBuilders = new ArrayList<>();
	private final List<ConfigDisplayCategory> displayCategories = new ArrayList<>();
	private final Path configFile;
	private final String localizationPath;
	private final IConfigSaveScheduler scheduler;

	public ConfigSchemaBuilder(Path configFile, String localizationPath, IConfigSaveScheduler scheduler) {
		this.configFile = configFile;
		this.localizationPath = localizationPath;
		this.scheduler = scheduler;
	}

	@Override
	public IConfigCategoryBuilder addCategory(String name) {
		if (!categoryNames.add(name)) {
			throw new IllegalArgumentException("There is already a category named: " + name);
		}
		ConfigCategoryBuilder category = new ConfigCategoryBuilder(localizationPath, name);
		this.categoryBuilders.add(category);
		return category;
	}

	@Override
	public void addDisplayCategory(ConfigDisplayCategory category) {
		this.displayCategories.add(category);
	}

	@Override
	public IConfigSchema build() {
		return new ConfigSchema(configFile, localizationPath, categoryBuilders, displayCategories, scheduler);
	}
}
