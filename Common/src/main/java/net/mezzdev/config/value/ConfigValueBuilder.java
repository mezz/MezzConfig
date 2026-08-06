package net.mezzdev.config.value;

import net.mezzdev.config.api.value.ConfigValueEditMode;
import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.mezzdev.config.api.value.IConfigValueBuilder;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.schema.IConfigEditorCategoryBuilder;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigEditorCategoryBuilder;
import net.mezzdev.config.util.ConfigNameUtil;
import net.mezzdev.config.util.ErrorUtil;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ConfigValueBuilder<T> implements IConfigValueBuilder<T> {
	private final ConfigCategoryBuilder categoryBuilder;
	private final String localizationPath;
	private final String name;
	private final T defaultValue;
	private final IConfigValueSerializer<T> serializer;
	private final Set<ConfigValueReference> legacyValueReferences = new LinkedHashSet<>();
	private final Map<ConfigValueReference, Function<String, T>> legacyValueMigrations = new LinkedHashMap<>();
	private final Set<ConfigEditorCategoryBuilder> editorCategoryBuilders = new LinkedHashSet<>();
	private ConfigValueEditMode editMode = ConfigValueEditMode.BATCH;
	private ConfigValueRestartRequirement restartRequirement = ConfigValueRestartRequirement.NONE;
	private @Nullable ConfigValue<T> configValue;

	public ConfigValueBuilder(
		ConfigCategoryBuilder categoryBuilder,
		String localizationPath,
		String name,
		T defaultValue,
		IConfigValueSerializer<T> serializer
	) {
		this.categoryBuilder = ErrorUtil.checkNotNull(categoryBuilder, "categoryBuilder");
		this.localizationPath = ErrorUtil.checkNotNull(localizationPath, "localizationPath");
		this.name = ConfigNameUtil.validateConfigName(name, "configValueName");
		this.defaultValue = ErrorUtil.checkNotNull(defaultValue, "defaultValue");
		this.serializer = ErrorUtil.checkNotNull(serializer, "serializer");
		if (!this.serializer.isValid(this.defaultValue)) {
			throw new IllegalArgumentException("Default value for '%s' is invalid: %s".formatted(this.name, this.defaultValue));
		}
	}

	public String getName() {
		return name;
	}

	public boolean isBuilt() {
		return configValue != null;
	}

	@Override
	public ConfigValueBuilder<T> addLegacyName(String legacyName) {
		checkNotBuilt();
		legacyName = ConfigNameUtil.validateConfigName(legacyName, "legacyValueName");
		if (legacyName.equals(name)) {
			throw new IllegalArgumentException("Legacy value name must not match the current value name: " + name);
		}
		return addLegacyValue(categoryBuilder.getName(), legacyName);
	}

	@Override
	public ConfigValueBuilder<T> addLegacyValue(String legacyCategoryName, String legacyValueName) {
		checkNotBuilt();
		ConfigValueReference reference = createLegacyReference(legacyCategoryName, legacyValueName);
		if (isCurrentValueReference(reference)) {
			throw new IllegalArgumentException("Legacy value reference must not match the current value: " + reference);
		}
		addLegacyValueReference(reference);
		return this;
	}

	@Override
	public ConfigValueBuilder<T> addLegacyValueMigration(
		String legacyCategoryName,
		String legacyValueName,
		Function<String, T> migration
	) {
		checkNotBuilt();
		ConfigValueReference reference = createLegacyReference(legacyCategoryName, legacyValueName);
		if (isCurrentValueReference(reference)) {
			throw new IllegalArgumentException("Legacy value reference must not match the current value: " + reference);
		}
		if (legacyValueReferences.contains(reference) || legacyValueMigrations.containsKey(reference)) {
			throw new IllegalArgumentException("There is already a legacy value reference: " + reference);
		}
		legacyValueMigrations.put(reference, ErrorUtil.checkNotNull(migration, "migration"));
		return this;
	}

	@Override
	public ConfigValueBuilder<T> setEditMode(ConfigValueEditMode editMode) {
		checkNotBuilt();
		this.editMode = ErrorUtil.checkNotNull(editMode, "editMode");
		return this;
	}

	@Override
	public ConfigValueBuilder<T> setRestartRequirement(ConfigValueRestartRequirement restartRequirement) {
		checkNotBuilt();
		this.restartRequirement = ErrorUtil.checkNotNull(restartRequirement, "restartRequirement");
		return this;
	}

	@Override
	public ConfigValueBuilder<T> addEditorCategory(IConfigEditorCategoryBuilder categoryBuilder) {
		checkNotBuilt();
		ConfigEditorCategoryBuilder editorCategoryBuilder = getEditorCategoryBuilder(categoryBuilder);
		ConfigCategoryBuilder currentCategoryBuilder = this.categoryBuilder;
		if (editorCategoryBuilder.getSchemaBuilder() != null &&
			currentCategoryBuilder.getSchemaBuilder() != null &&
			editorCategoryBuilder.getSchemaBuilder() != currentCategoryBuilder.getSchemaBuilder()
		) {
			throw new IllegalArgumentException("Editor category must belong to the same schema: " + editorCategoryBuilder.getName());
		}
		if (!editorCategoryBuilders.add(editorCategoryBuilder)) {
			throw new IllegalArgumentException("There is already an editor category: " + editorCategoryBuilder.getName());
		}
		return this;
	}

	private ConfigEditorCategoryBuilder getEditorCategoryBuilder(IConfigEditorCategoryBuilder categoryBuilder) {
		ErrorUtil.checkNotNull(categoryBuilder, "categoryBuilder");
		if (categoryBuilder instanceof ConfigEditorCategoryBuilder configEditorCategoryBuilder) {
			return configEditorCategoryBuilder;
		}
		throw new IllegalArgumentException("Editor category must be created by MezzConfig.");
	}

	@Override
	public ConfigValue<T> build() {
		checkNotBuilt();
		ConfigValue<T> value = new ConfigValue<>(
			localizationPath,
			name,
			defaultValue,
			serializer,
			editMode,
			restartRequirement,
			editorCategoryBuilders
		);
		this.configValue = categoryBuilder.addValue(
			value,
			legacyValueReferences,
			legacyValueMigrations
		);
		return this.configValue;
	}

	private void addLegacyValueReference(ConfigValueReference reference) {
		if (legacyValueMigrations.containsKey(reference) || !legacyValueReferences.add(reference)) {
			throw new IllegalArgumentException("There is already a legacy value reference: " + reference);
		}
	}

	private ConfigValueReference createLegacyReference(String legacyCategoryName, String legacyValueName) {
		legacyCategoryName = ConfigNameUtil.validateConfigName(legacyCategoryName, "legacyCategoryName");
		legacyValueName = ConfigNameUtil.validateConfigName(legacyValueName, "legacyValueName");
		return new ConfigValueReference(legacyCategoryName, legacyValueName);
	}

	private boolean isCurrentValueReference(ConfigValueReference reference) {
		return reference.categoryName().equals(categoryBuilder.getName()) &&
			reference.valueName().equals(name);
	}

	private void checkNotBuilt() {
		if (configValue != null) {
			throw new IllegalStateException("Config value has already been built: " + name);
		}
	}
}
