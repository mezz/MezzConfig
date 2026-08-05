package net.mezzdev.config.api.value;

import net.mezzdev.config.api.schema.IConfigCategory;
import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Represents a config value.
 * Config values can be read or updated by mods.
 * <p>
 * These config values are automatically synced with the config file.
 * {@link #getValue()} will automatically update based on changes to the file,
 * and using {@link #set} will automatically update the file.
 * <p>
 * Add a value to your category with the add methods on {@link IConfigCategoryBuilder}.
 * Get registered values here: {@link IConfigCategory#getConfigValues()}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigValue<T> {
	/**
	 * Get the name of this config value.
	 *
	 * @since 0.1.0
	 */
	String getName();

	/**
	 * Get the translation key used for this config value's name.
	 *
	 * @since 0.1.0
	 */
	String getLocalizationKey();

	/**
	 * Get the current value.
	 * This will automatically update and load from the config file if there are changes.
	 *
	 * @since 0.1.0
	 */
	T getValue();

	/**
	 * Get the default value.
	 *
	 * @since 0.1.0
	 */
	T getDefaultValue();

	/**
	 * Get the edit mode hint for this value.
	 * <p>
	 * Config editors can use this to decide when changes should be saved.
	 *
	 * @since 0.1.0
	 */
	ConfigValueEditMode getEditMode();

	/**
	 * Get the category names where config editors should show this value.
	 * <p>
	 * If this is empty, config editors can show the value in its storage category. Values may be shown in multiple
	 * editor categories.
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<String> getEditorCategoryNames();

	/**
	 * Set the config value to the given value.
	 * This will automatically mark the config file as dirty so that it will save the new values.
	 * <p>
	 * Use {@link net.mezzdev.config.api.schema.IConfigSchema#batchUpdate(java.util.function.Consumer)} to update
	 * several config values together.
	 *
	 * @since 0.1.0
	 */
	boolean set(T value);

	/**
	 * Add a listener that is called with the applied change when this config value changes.
	 *
	 * @param listener callback accepting the applied change
	 *
	 * @since 0.1.0
	 */
	void addListener(IConfigValueChangeListener<T> listener);

	/**
	 * Add a listener that is called with all changes from a batch containing this config value.
	 * <p>
	 * Use this when the listener needs to observe other config values updated in the same batch.
	 *
	 * @since 0.1.0
	 */
	void addBatchListener(IConfigValueBatchChangeListener listener);

	/**
	 * Get the helper for serializing values to and from Strings, and validating values.
	 *
	 * @since 0.1.0
	 */
	IConfigValueSerializer<T> getSerializer();
}
