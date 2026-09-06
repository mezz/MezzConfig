package net.mezzdev.config.api.value.builder;

import net.mezzdev.config.api.schema.builder.IConfigEditorCategoryBuilder;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.editor.ConfigValueEditMode;
import net.mezzdev.config.api.value.editor.ConfigValueRestartRequirement;
import net.mezzdev.config.api.value.editor.IConfigValueEditorInfo;
import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;

/**
 * Configures optional behavior before adding a value to its category.
 * <p>
 * Get an instance from one of the value methods on
 * {@link net.mezzdev.config.api.schema.builder.IConfigCategoryBuilder}. Most values can be built immediately. Use this builder
 * when a setting needs a restart, a different config-screen category, or a legacy source imported with
 * {@link net.mezzdev.config.api.schema.builder.IConfigSchemaBuilder#setLegacySources(java.util.List)}.
 *
 * @param <T> effectively immutable value type with stable equality
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigValueBuilder<T> {
	/**
	 * Set the edit mode hint for this value.
	 * <p>
	 * Config editors can use this to decide when changes should be saved. If this is not called, values use
	 * {@link ConfigValueEditMode#BATCH}.
	 *
	 * @param editMode edit mode hint for config editors
	 * @return this builder
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<T> setEditMode(ConfigValueEditMode editMode);

	/**
	 * Set when saved changes to this value become effective.
	 * <p>
	 * Until that lifecycle boundary, {@link IConfigValue#get()} retains the effective value and
	 * {@link IConfigValueEditorInfo#getPendingValue()} returns the saved change. If this is not called, values use
	 * {@link ConfigValueRestartRequirement#NONE} and update immediately.
	 *
	 * @param restartRequirement when saved changes become effective
	 * @return this builder
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<T> setRestartRequirement(ConfigValueRestartRequirement restartRequirement);

	/**
	 * Add a category where config editors should show this value.
	 * <p>
	 * Pass an editor category builder or storage category builder from the same schema. The category does not need to
	 * be built yet. If no editor categories are added, config editors can show the value in its storage category.
	 * Values may be added to multiple editor categories.
	 *
	 * @param categoryBuilder category where config editors should show this value
	 * @return this builder
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<T> addEditorCategory(IConfigEditorCategoryBuilder categoryBuilder);

	/**
	 * Map this value from its old name when importing a legacy MezzConfig source.
	 * <p>
	 * Use this with {@link net.mezzdev.config.api.schema.builder.IConfigSchemaBuilder#setLegacySources(java.util.List)} when
	 * this value was renamed within the same storage category, but its serialized format did not change. Legacy mappings
	 * are considered only while importing a source into a destination config that does not exist yet.
	 *
	 * @param legacyName old stable storage name for this value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<T> addLegacyName(String legacyName);

	/**
	 * Map this value from its old storage location when importing a legacy MezzConfig source.
	 * <p>
	 * Use this with {@link net.mezzdev.config.api.schema.builder.IConfigSchemaBuilder#setLegacySources(java.util.List)} when
	 * this value moved from another category, another name, or both, but its serialized format did not change. Legacy
	 * mappings are considered only while importing a source into a destination config that does not exist yet.
	 *
	 * @param legacyCategoryName old stable storage category name
	 * @param legacyValueName old stable storage value name
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<T> addLegacyValue(String legacyCategoryName, String legacyValueName);

	/**
	 * Convert a value previously stored by MezzConfig when its type or serialized form changed.
	 * <p>
	 * Use this with {@link net.mezzdev.config.api.schema.builder.IConfigSchemaBuilder#setLegacySources(java.util.List)} for one
	 * setting that moved to a new category or name and now uses a different representation. While importing the selected
	 * legacy source, MezzConfig reads the old value with {@code legacySerializer} and passes the typed result to
	 * {@code migration}. This conversion is not run while loading an existing destination config.
	 * <p>
	 * To import a file that was not written by MezzConfig, use
	 * {@link net.mezzdev.config.api.schema.builder.IConfigSchemaBuilder#setLegacyMigration(java.util.List,
	 * net.mezzdev.config.api.migration.IConfigMigrator)} instead.
	 *
	 * @param legacyCategoryName old stable storage category name
	 * @param legacyValueName old stable storage value name
	 * @param legacySerializer serializer for the old value type and storage format
	 * @param migration converts a usable old value into a valid current value; it must be deterministic and thread-safe
	 * @param <U> old value type
	 *
	 * @since 0.1.0
	 */
	<U> IConfigValueBuilder<T> addLegacyValueMigration(
		String legacyCategoryName,
		String legacyValueName,
		IConfigValueSerializer<U> legacySerializer,
		Function<U, T> migration
	);

	/**
	 * Build and add the config value to its category.
	 * A value builder may only be built once.
	 *
	 * @since 0.1.0
	 */
	IConfigValue<T> build();
}
