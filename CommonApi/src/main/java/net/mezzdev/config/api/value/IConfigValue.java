package net.mezzdev.config.api.value;

import net.mezzdev.config.api.schema.IConfigCategory;
import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import net.mezzdev.config.api.schema.IConfigEditorCategory;
import net.mezzdev.config.api.schema.IConfigSchema;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.Consumer;

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
 * @param <T> an effectively immutable value type with stable {@link Object#equals(Object)} behavior
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
	 * <p>
	 * Values are immutable by contract. Built-in list values return an unmodifiable snapshot.
	 *
	 * @since 0.1.0
	 */
	T getValue();

	/**
	 * Get the default value.
	 * <p>
	 * Values are immutable by contract. Built-in list values return an unmodifiable snapshot.
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
	 * Get the restart requirement hint for this value.
	 * <p>
	 * Config editors can use this to explain when changed values take effect. MezzConfig still updates this value when
	 * it changes through the API or config file. Mods that only apply a value at startup or world load should read it
	 * during that lifecycle.
	 *
	 * @since 0.1.0
	 */
	ConfigValueRestartRequirement getRestartRequirement();

	/**
	 * Get the categories where config editors should show this value.
	 * <p>
	 * If this is empty, config editors can show the value in the category that contains it.
	 * Values may be shown in multiple editor categories. Categories are returned in editor category order from
	 * {@link IConfigSchema#getEditorCategories()}.
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<? extends IConfigEditorCategory> getEditorCategories();

	/**
	 * Set the config value to the given value.
	 * This will automatically mark the config file as dirty so that it will save the new value.
	 * Built-in list values are copied to an unmodifiable snapshot before this method returns.
	 * <p>
	 * Use {@link IConfigSchema#batchUpdate(Consumer)} to update
	 * several config values together.
	 *
	 * @param value new value
	 * @return {@code true} if the value changed, or {@code false} if the value was valid but equal to the current value
	 *
	 * @throws IllegalArgumentException if the value is invalid
	 * @throws IllegalStateException if this value's context-specific schema is currently inactive
	 *
	 * @since 0.1.0
	 */
	boolean set(T value);

	/**
	 * Add a listener that is called with the applied change when this config value changes.
	 * See {@link IConfigValueChangeListener} for callback execution and failure behavior.
	 *
	 * @param listener callback accepting the applied change
	 * @return a callback that removes this listener
	 *
	 * @since 0.1.0
	 */
	Runnable addListener(IConfigValueChangeListener<T> listener);

	/**
	 * Add a listener that is called with all changes from a batch containing this config value.
	 * <p>
	 * Use this when the listener needs to observe other config values updated in the same batch.
	 * See {@link IConfigValueBatchChangeListener} for callback execution and failure behavior.
	 *
	 * @param listener callback accepting the applied changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.1.0
	 */
	Runnable addBatchListener(IConfigValueBatchChangeListener listener);

	/**
	 * Get the helper for serializing values to and from Strings, and validating values.
	 *
	 * @since 0.1.0
	 */
	IConfigValueSerializer<T> getSerializer();
}
