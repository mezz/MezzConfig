package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.ConfigOwnership;
import net.mezzdev.config.api.schema.ConfigScope;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.server.ServerConfigKey;
import net.mezzdev.config.util.ConfigNameUtil;
import net.mezzdev.config.util.ErrorUtil;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class ConfigSchemaBuilder implements IConfigSchemaBuilder {
	private final Set<String> categoryNames = new HashSet<>();
	private final List<ConfigCategoryBuilder> categoryBuilders = new ArrayList<>();
	private final List<ConfigEditorCategoryBuilder> editorCategoryBuilders = new ArrayList<>();
	private final String modId;
	private final Function<ConfigScope, ConfigSchemaPathResolver> pathResolverFactory;
	private final String localizationPath;
	private final ConfigManager configManager;
	private final ConfigOwnership ownership;
	private final String configFileName;
	private final boolean registrationEnabled;
	private ConfigScope scope = ConfigScope.INSTALLATION;
	private boolean built;

	public ConfigSchemaBuilder(Path configFile, String localizationPath, ConfigManager configManager) {
		this(ConfigSchema.DEFAULT_MOD_ID, new StaticConfigSchemaPathResolver(configFile), localizationPath, configManager);
	}

	public ConfigSchemaBuilder(ConfigSchemaPathResolver pathResolver, String localizationPath, ConfigManager configManager) {
		this(ConfigSchema.DEFAULT_MOD_ID, pathResolver, localizationPath, configManager);
	}

	public ConfigSchemaBuilder(String modId, Path configFile, String localizationPath, ConfigManager configManager) {
		this(modId, new StaticConfigSchemaPathResolver(configFile), localizationPath, configManager);
	}

	public ConfigSchemaBuilder(String modId, ConfigSchemaPathResolver pathResolver, String localizationPath, ConfigManager configManager) {
		this(modId, ignored -> pathResolver, localizationPath, configManager, ConfigOwnership.CLIENT, "config.ini");
	}

	public ConfigSchemaBuilder(
		String modId,
		Function<ConfigScope, ConfigSchemaPathResolver> pathResolverFactory,
		String localizationPath,
		ConfigManager configManager,
		ConfigOwnership ownership,
		String configFileName
	) {
		this(modId, pathResolverFactory, localizationPath, configManager, ownership, configFileName, true);
	}

	public ConfigSchemaBuilder(
		String modId,
		Function<ConfigScope, ConfigSchemaPathResolver> pathResolverFactory,
		String localizationPath,
		ConfigManager configManager,
		ConfigOwnership ownership,
		String configFileName,
		boolean registrationEnabled
	) {
		this.modId = ConfigSchema.validateModId(modId);
		this.pathResolverFactory = ErrorUtil.checkNotNull(pathResolverFactory, "pathResolverFactory");
		this.localizationPath = ErrorUtil.checkNotNull(localizationPath, "localizationPath");
		this.configManager = ErrorUtil.checkNotNull(configManager, "configManager");
		this.ownership = ErrorUtil.checkNotNull(ownership, "ownership");
		this.configFileName = ErrorUtil.checkNotNull(configFileName, "configFileName");
		this.registrationEnabled = registrationEnabled;
	}

	@Override
	public ConfigSchemaBuilder setScope(ConfigScope scope) {
		checkNotBuilt();
		this.scope = ErrorUtil.checkNotNull(scope, "scope");
		return this;
	}

	@Override
	public ConfigCategoryBuilder addCategory(String name) {
		checkNotBuilt();
		name = ConfigNameUtil.validateConfigName(name, "categoryName");
		if (!categoryNames.add(name)) {
			throw new IllegalArgumentException("There is already a category named: " + name);
		}
		ConfigCategoryBuilder category = new ConfigCategoryBuilder(this, localizationPath, name);
		this.categoryBuilders.add(category);
		this.editorCategoryBuilders.add(category);
		return category;
	}

	@Override
	public ConfigEditorCategoryBuilder addEditorCategory(String name) {
		checkNotBuilt();
		name = ConfigNameUtil.validateConfigName(name, "categoryName");
		if (!categoryNames.add(name)) {
			throw new IllegalArgumentException("There is already a category named: " + name);
		}
		ConfigEditorCategoryBuilder category = new ConfigEditorCategoryBuilder(this, localizationPath, name);
		this.editorCategoryBuilders.add(category);
		return category;
	}

	@Override
	public ConfigSchema build() {
		checkNotBuilt();
		built = true;
		ConfigSchemaPathResolver pathResolver = ErrorUtil.checkNotNull(
			pathResolverFactory.apply(scope),
			"pathResolver"
		);
		@Nullable
		ServerConfigKey serverKey = null;
		if (ownership == ConfigOwnership.SERVER && scope == ConfigScope.WORLD) {
			serverKey = new ServerConfigKey(modId, configFileName);
		}
		ConfigSchema schema = new ConfigSchema(
			modId,
			pathResolver,
			categoryBuilders,
			editorCategoryBuilders,
			configManager.getSaveScheduler(),
			ownership,
			scope,
			serverKey
		);
		if (registrationEnabled) {
			configManager.registerSchema(schema);
		}
		return schema;
	}

	private void checkNotBuilt() {
		if (built) {
			throw new IllegalStateException("Config schema has already been built.");
		}
	}
}
