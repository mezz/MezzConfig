package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.schema.builder.IConfigSchemaBuilder;
import net.mezzdev.config.api.migration.IConfigMigrator;
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

public class ConfigSchemaBuilder implements IConfigSchemaBuilder {
	private final Set<String> categoryNames = new HashSet<>();
	private final List<ConfigCategoryBuilder> categoryBuilders = new ArrayList<>();
	private final List<ConfigEditorCategoryBuilder> editorCategoryBuilders = new ArrayList<>();
	private final String id;
	private final String modId;
	private final ConfigSchemaPathResolver pathResolver;
	private final String localizationPath;
	private final ConfigManager configManager;
	private final ConfigSchemaType type;
	private final @Nullable ServerConfigKey serverKey;
	private final boolean registrationEnabled;
	private @Nullable ConfigMigrationSpec migrationSpec;
	private boolean built;

	public ConfigSchemaBuilder(Path configFile, String localizationPath, ConfigManager configManager) {
		this(ConfigSchema.DEFAULT_MOD_ID, new StaticConfigSchemaPathResolver(configFile), localizationPath, configManager);
	}

	@Override
	public ConfigSchemaBuilder setLegacySources(List<Path> legacyPaths) {
		checkCanRegisterMigration();
		migrationSpec = ConfigMigrationSpec.alternateSources(legacyPaths);
		return this;
	}

	@Override
	public ConfigSchemaBuilder setLegacyMigration(List<Path> legacyPaths, IConfigMigrator migrator) {
		checkCanRegisterMigration();
		migrationSpec = ConfigMigrationSpec.custom(legacyPaths, migrator);
		return this;
	}

	private void checkCanRegisterMigration() {
		checkNotBuilt();
		if (migrationSpec != null) {
			throw new IllegalStateException("A legacy source or migration is already registered for this schema.");
		}
	}

	public ConfigSchemaBuilder(ConfigSchemaPathResolver pathResolver, String localizationPath, ConfigManager configManager) {
		this(ConfigSchema.DEFAULT_MOD_ID, pathResolver, localizationPath, configManager);
	}

	public ConfigSchemaBuilder(String modId, Path configFile, String localizationPath, ConfigManager configManager) {
		this(modId, new StaticConfigSchemaPathResolver(configFile), localizationPath, configManager);
	}

	public ConfigSchemaBuilder(String modId, ConfigSchemaPathResolver pathResolver, String localizationPath, ConfigManager configManager) {
		this(modId, pathResolver, localizationPath, configManager, ConfigSchemaType.CLIENT, null);
	}

	public ConfigSchemaBuilder(
		String modId,
		ConfigSchemaPathResolver pathResolver,
		String localizationPath,
		ConfigManager configManager,
		ConfigSchemaType type,
		@Nullable ServerConfigKey serverKey
	) {
		this(modId, pathResolver, localizationPath, configManager, type, serverKey, true);
	}

	public ConfigSchemaBuilder(
		String modId,
		ConfigSchemaPathResolver pathResolver,
		String localizationPath,
		ConfigManager configManager,
		ConfigSchemaType type,
		@Nullable ServerConfigKey serverKey,
		boolean registrationEnabled
	) {
		this(
			getDefaultId(modId, serverKey),
			modId,
			pathResolver,
			localizationPath,
			configManager,
			type,
			serverKey,
			registrationEnabled
		);
	}

	private static String getDefaultId(String modId, @Nullable ServerConfigKey serverKey) {
		if (serverKey == null) {
			return modId;
		}
		return serverKey.configFileName();
	}

	public ConfigSchemaBuilder(
		String id,
		String modId,
		ConfigSchemaPathResolver pathResolver,
		String localizationPath,
		ConfigManager configManager,
		ConfigSchemaType type,
		@Nullable ServerConfigKey serverKey,
		boolean registrationEnabled
	) {
		this.id = ErrorUtil.checkNotNull(id, "id");
		this.modId = ConfigSchema.validateModId(modId);
		this.pathResolver = ErrorUtil.checkNotNull(pathResolver, "pathResolver");
		this.localizationPath = ErrorUtil.checkNotNull(localizationPath, "localizationPath");
		this.configManager = ErrorUtil.checkNotNull(configManager, "configManager");
		this.type = ErrorUtil.checkNotNull(type, "type");
		this.serverKey = serverKey;
		ConfigSchema.validateServerKey(type, serverKey);
		this.registrationEnabled = registrationEnabled;
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
		if (categoryBuilders.isEmpty()) {
			throw new IllegalStateException("Config schema must have at least one storage category.");
		}
		built = true;
		ConfigSchema schema = new ConfigSchema(
			id,
			modId,
			pathResolver,
			categoryBuilders,
			editorCategoryBuilders,
			configManager.getSaveScheduler(),
			type,
			serverKey,
			migrationSpec
		);
		if (registrationEnabled) {
			configManager.registerSchema(schema);
		} else {
			schema.completeInactiveMigration();
		}
		return schema;
	}

	private void checkNotBuilt() {
		if (built) {
			throw new IllegalStateException("Config schema has already been built.");
		}
	}
}
