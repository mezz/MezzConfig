package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.value.change.IConfigValueBatchChangeListener;
import net.mezzdev.config.file.ConfigSerializer;
import net.mezzdev.config.server.ServerConfigKey;
import net.mezzdev.config.util.ErrorUtil;
import net.mezzdev.config.util.ListenerList;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.value.ConfigValueOwner;
import net.mezzdev.deduplicatingrunner.DelayedTaskScheduler;
import net.mezzdev.filewatcher.FileWatcher;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class ConfigSchemaDefinition {
	private static final ConfigValueOwner DECLARED_VALUES = new ConfigValueOwner() {
		@Override
		public <T> T getEffectiveValue(ConfigValue<T> value) {
			return value.getDefaultValue();
		}

		@Override
		public <T> T getPendingValue(ConfigValue<T> value) {
			return value.getDefaultValue();
		}

		@Override
		public <T> boolean setValue(ConfigValue<T> value, T newValue) {
			throw new IllegalStateException("Config schema has no active backing file.");
		}

		@Override
		public void markDirty() {}
	};

	final String id;
	final String modId;
	final ConfigSchemaPathResolver pathResolver;
	final ConfigSchemaType type;
	final ConfigSchemaMode mode;
	final @Nullable ServerConfigKey serverKey;
	final List<ConfigCategory> categories;
	final List<ConfigEditorCategory> editorCategories;
	final @Nullable ConfigMigrationSpec migrationSpec;
	final DelayedTaskScheduler scheduler;
	final ListenerList<IConfigValueBatchChangeListener> batchListeners = new ListenerList<>();
	final ListenerList<IConfigValueBatchChangeListener> pendingBatchListeners = new ListenerList<>();
	private boolean built;

	public ConfigSchemaDefinition(
		String id,
		String modId,
		ConfigSchemaPathResolver pathResolver,
		List<ConfigCategoryBuilder> categoryBuilders,
		List<ConfigEditorCategoryBuilder> editorCategoryBuilders,
		DelayedTaskScheduler scheduler,
		ConfigSchemaType type,
		@Nullable ServerConfigKey serverKey,
		@Nullable ConfigMigrationSpec migrationSpec
	) {
		this.id = ErrorUtil.checkNotBlank(id, "id");
		this.modId = ErrorUtil.checkNotBlank(modId, "modId");
		this.pathResolver = ErrorUtil.checkNotNull(pathResolver, "pathResolver");
		this.type = ErrorUtil.checkNotNull(type, "type");
		this.mode = ConfigSchemaMode.forSchema(type);
		this.serverKey = serverKey;
		this.migrationSpec = migrationSpec;
		validateServerKey(type, serverKey);
		if (categoryBuilders.isEmpty()) {
			throw new IllegalStateException("Config schema must have at least one storage category.");
		}
		Map<ConfigEditorCategoryBuilder, ConfigEditorCategory> editorCategoryMap = new IdentityHashMap<>();
		List<ConfigCategory> categories = new ArrayList<>();
		for (ConfigCategoryBuilder categoryBuilder : categoryBuilders) {
			ConfigCategory category = categoryBuilder.build();
			editorCategoryMap.put(categoryBuilder, category);
			categories.add(category);
		}
		List<ConfigEditorCategory> editorCategories = new ArrayList<>();
		for (ConfigEditorCategoryBuilder editorCategoryBuilder : editorCategoryBuilders) {
			ConfigEditorCategory category = editorCategoryMap.get(editorCategoryBuilder);
			if (category == null) {
				category = editorCategoryBuilder.build();
				editorCategoryMap.put(editorCategoryBuilder, category);
			}
			editorCategories.add(category);
		}
		categoryBuilders.forEach(categoryBuilder -> categoryBuilder.resolveEditorCategories(editorCategoryBuilders, editorCategoryMap));
		this.categories = List.copyOf(categories);
		this.editorCategories = List.copyOf(editorCategories);
		ConfigSerializer.validatePendingSave(this.categories, mode.serializationSettings(), Map.of());
		this.scheduler = ErrorUtil.checkNotNull(scheduler, "scheduler");
		bindValues(DECLARED_VALUES);
	}

	static void validateServerKey(ConfigSchemaType type, @Nullable ServerConfigKey serverKey) {
		if (type == ConfigSchemaType.SERVER && serverKey == null) {
			throw new IllegalArgumentException("Server config schemas require a server key.");
		}
		if (type != ConfigSchemaType.SERVER && serverKey != null) {
			throw new IllegalArgumentException("Only server config schemas can have a server key.");
		}
	}

	public ConfigSchema initialize(@Nullable FileWatcher fileWatcher, boolean logUntranslatedKeys) {
		return initialize(fileWatcher, logUntranslatedKeys, paths -> {}, schema -> {});
	}

	public synchronized ConfigSchema initialize(
		@Nullable FileWatcher fileWatcher,
		boolean logUntranslatedKeys,
		Consumer<? super Collection<Path>> pathReservation,
		Consumer<ConfigSchema> publish
	) {
		checkNotBuilt();
		built = true;
		try {
			return ConfigSchema.create(this, fileWatcher, logUntranslatedKeys, pathReservation, publish);
		} catch (RuntimeException | Error e) {
			built = false;
			throw e;
		}
	}

	synchronized ConfigSchema createInactive() {
		checkNotBuilt();
		built = true;
		return ConfigSchema.createInactive(this);
	}

	private void checkNotBuilt() {
		if (built) {
			throw new IllegalStateException("Config schema definition has already been initialized.");
		}
	}

	void bindValues(ConfigValueOwner owner) {
		categories.forEach(category -> category.getConfigValues().forEach(value -> value.setOwner(owner)));
	}

	void resetValues() {
		categories.forEach(category -> category.getConfigValues().forEach(ConfigValue::resetAllToDefaultWithoutNotifying));
		bindValues(DECLARED_VALUES);
	}

	public String getId() {
		return id;
	}

	public String getModId() {
		return modId;
	}

	public ConfigSchemaType getType() {
		return type;
	}

	public ServerConfigKey getServerKey() {
		if (serverKey == null) {
			throw new IllegalStateException("Config schema is not a server schema.");
		}
		return serverKey;
	}

	public Optional<Path> getRegistrationPath() {
		return pathResolver.resolvePath().map(Path::normalize);
	}

	public Optional<Path> getDefaultPath() {
		return pathResolver.resolveDefaultPath().map(Path::normalize);
	}

	public List<ConfigCategory> getCategories() {
		return categories;
	}

	public List<ConfigEditorCategory> getEditorCategories() {
		return editorCategories;
	}

	public Runnable addBatchListener(IConfigValueBatchChangeListener listener) {
		return batchListeners.add(ErrorUtil.checkNotNull(listener, "listener"));
	}

	public Runnable addPendingBatchListener(IConfigValueBatchChangeListener listener) {
		return pendingBatchListeners.add(ErrorUtil.checkNotNull(listener, "listener"));
	}
}
