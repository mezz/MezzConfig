package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.util.ConfigNameUtil;
import net.mezzdev.config.util.ErrorUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigSchemaBuilder implements IConfigSchemaBuilder {
	private final Set<String> categoryNames = new HashSet<>();
	private final List<ConfigCategoryBuilder> categoryBuilders = new ArrayList<>();
	private final Path configFile;
	private final String localizationPath;
	private final ConfigManager configManager;
	private boolean built;

	public ConfigSchemaBuilder(Path configFile, String localizationPath, ConfigManager configManager) {
		this.configFile = ErrorUtil.checkNotNull(configFile, "configFile");
		this.localizationPath = ErrorUtil.checkNotNull(localizationPath, "localizationPath");
		this.configManager = ErrorUtil.checkNotNull(configManager, "configManager");
	}

	@Override
	public ConfigCategoryBuilder addCategory(String name) {
		checkNotBuilt();
		name = ConfigNameUtil.validateConfigName(name, "categoryName");
		if (!categoryNames.add(name)) {
			throw new IllegalArgumentException("There is already a category named: " + name);
		}
		ConfigCategoryBuilder category = new ConfigCategoryBuilder(localizationPath, name);
		this.categoryBuilders.add(category);
		return category;
	}

	@Override
	public ConfigSchema build() {
		checkNotBuilt();
		built = true;
		ConfigSchema schema = new ConfigSchema(
			configFile,
			categoryBuilders,
			configManager.getSaveScheduler()
		);
		configManager.registerSchema(schema);
		return schema;
	}

	private void checkNotBuilt() {
		if (built) {
			throw new IllegalStateException("Config schema has already been built: " + configFile);
		}
	}
}
