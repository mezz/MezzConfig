package net.mezzdev.config.value;

import net.mezzdev.config.api.value.IConfigValueBuilder;
import net.mezzdev.config.api.value.ConfigValueEditMode;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.util.ConfigNameUtil;
import net.mezzdev.config.util.ErrorUtil;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

public class ConfigValueBuilder<T> implements IConfigValueBuilder<T> {
	private final ConfigCategoryBuilder categoryBuilder;
	private final String localizationPath;
	private final String name;
	private final T defaultValue;
	private final IConfigValueSerializer<T> serializer;
	private final Set<String> legacyNames = new LinkedHashSet<>();
	private final Set<String> editorCategoryNames = new LinkedHashSet<>();
	private @Nullable Function<String, T> legacyValueMigration;
	private ConfigValueEditMode editMode = ConfigValueEditMode.BATCH;
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
		if (!legacyNames.add(legacyName)) {
			throw new IllegalArgumentException("There is already a legacy value name: " + legacyName);
		}
		return this;
	}

	@Override
	public ConfigValueBuilder<T> addLegacyValueMigration(Function<String, T> migration) {
		checkNotBuilt();
		if (legacyValueMigration != null) {
			throw new IllegalStateException("Config value already has a legacy value migration: " + name);
		}
		this.legacyValueMigration = ErrorUtil.checkNotNull(migration, "migration");
		return this;
	}

	@Override
	public ConfigValueBuilder<T> setEditMode(ConfigValueEditMode editMode) {
		checkNotBuilt();
		this.editMode = ErrorUtil.checkNotNull(editMode, "editMode");
		return this;
	}

	@Override
	public ConfigValueBuilder<T> addEditorCategory(String categoryName) {
		checkNotBuilt();
		categoryName = ConfigNameUtil.validateConfigName(categoryName, "editorCategoryName");
		if (!editorCategoryNames.add(categoryName)) {
			throw new IllegalArgumentException("There is already an editor category name: " + categoryName);
		}
		return this;
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
			editorCategoryNames
		);
		this.configValue = categoryBuilder.addValue(value, legacyNames, legacyValueMigration);
		return this.configValue;
	}

	private void checkNotBuilt() {
		if (configValue != null) {
			throw new IllegalStateException("Config value has already been built: " + name);
		}
	}
}
